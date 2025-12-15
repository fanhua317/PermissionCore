package com.permacore.iam.security.filter;

import cn.hutool.core.util.StrUtil;
import com.permacore.iam.utils.JwtUtil;
import com.permacore.iam.utils.RedisCacheUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 统一的 JWT 授权过滤器，确保 SecurityConfig 中引用的 OncePerRequestFilter 存在。
 */
@RequiredArgsConstructor
public class JwtAuthorizationOnceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthorizationOnceFilter.class);

    private final JwtUtil jwtUtil;
    private final RedisCacheUtil redisCacheUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (StrUtil.isBlank(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = Long.parseLong(claims.getSubject());

            String currentVersion = redisCacheUtil.getJwtVersion(userId);
            if (currentVersion == null || !StrUtil.equals(currentVersion, jwtUtil.getJtiFromToken(token))) {
                log.warn("Token 版本失效，userId={}", userId);
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            List<SimpleGrantedAuthority> authorities = resolveAuthorities(claims);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception ex) {
            log.warn("JWT 认证失败: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
        return StrUtil.isNotBlank(bearer) && bearer.startsWith("Bearer ") ? bearer.substring(7) : null;
    }

    private List<SimpleGrantedAuthority> resolveAuthorities(Claims claims) {
        Object permissionsObj = claims.get("permissions");
        if (permissionsObj == null) {
            return Collections.emptyList();
        }
        List<String> permissionList = new ArrayList<>();
        if (permissionsObj instanceof List<?>) {
            ((List<?>) permissionsObj).forEach(item -> {
                if (item != null) {
                    permissionList.add(item.toString());
                }
            });
        } else if (permissionsObj instanceof String) {
            String permissions = (String) permissionsObj;
            if (StrUtil.isNotBlank(permissions)) {
                Stream.of(permissions.split(","))
                        .map(String::trim)
                        .filter(StrUtil::isNotEmpty)
                        .forEach(permissionList::add);
            }
        }
        return permissionList.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}

