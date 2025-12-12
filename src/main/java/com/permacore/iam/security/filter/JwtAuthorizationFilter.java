package com.permacore.iam.security.filter;

import cn.hutool.core.util.StrUtil;
import com.permacore.iam.utils.JwtUtil;
import com.permacore.iam.utils.RedisCacheUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT授权过滤器（验证每次请求的Token）
 */
@Component
@RequiredArgsConstructor
public class JwtAuthorizationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthorizationFilter.class);

    private final JwtUtil jwtUtil;
    private final RedisCacheUtil redisCacheUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. 获取Token
        String token = resolveToken(request);
        if (StrUtil.isBlank(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 2. 解析Token
            Claims claims = jwtUtil.parseToken(token);
            Long userId = Long.parseLong(claims.getSubject());

            // 3. 验证JWT版本号（强制登出检测）- 仅在 Redis 可用时验证
            String tokenVersion = jwtUtil.getJtiFromToken(token);
            String cachedVersion = redisCacheUtil.getJwtVersion(userId);

            // 如果缓存版本存在且不匹配，说明被强制登出
            if (cachedVersion != null && !tokenVersion.equals(cachedVersion)) {
                log.warn("Token版本不匹配，可能已被强制登出: userId={}", userId);
                filterChain.doFilter(request, response);
                return;
            }
            // 如果 cachedVersion 为 null，可能是 Redis 不可用或首次登录，允许通过

            // 4. 构建认证信息
            String permissionsStr = claims.get("permissions", String.class);
            List<SimpleGrantedAuthority> authorities = Collections.emptyList();

            if (StrUtil.isNotBlank(permissionsStr)) {
                // 先移除可能的方括号与双引号，然后按照逗号拆分
                String cleaned = permissionsStr.replace("[", "").replace("]", "").replace("\"", "");
                authorities = Arrays.stream(cleaned.split(","))
                        .filter(StrUtil::isNotBlank)
                        .map(String::trim)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId, // Principal存用户ID
                            null,
                            authorities
                    );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // 5. 存入Security上下文
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("JWT鉴权成功: userId={}, uri={}", userId, request.getRequestURI());

        } catch (Exception e) {
            log.warn("JWT鉴权失败: {}", e.getMessage());
            // 清除Security上下文
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中解析Token
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StrUtil.isNotBlank(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
