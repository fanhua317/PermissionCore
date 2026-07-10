package com.permacore.iam.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.utils.JwtUtil;
import com.permacore.iam.utils.RedisCacheUtil;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.domain.vo.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
import org.springframework.lang.NonNull;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 统一的 JWT 授权过滤器，确保 SecurityConfig 中引用的 OncePerRequestFilter 存在。
 */
@RequiredArgsConstructor
public class JwtAuthorizationOnceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthorizationOnceFilter.class);

    private final JwtUtil jwtUtil;
    private final RedisCacheUtil redisCacheUtil;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Claims claims;
        Long userId;
        List<SimpleGrantedAuthority> authorities;
        try {
            claims = jwtUtil.parseToken(token);
            if (!jwtUtil.isAccessToken(claims)) {
                throw new IllegalArgumentException("只允许使用 Access Token 访问受保护资源");
            }
            userId = Long.parseLong(claims.getSubject());
            authorities = resolveAuthorities(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT 认证失败: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String currentVersion = redisCacheUtil.getJwtVersion(userId);
            String tokenSessionId = jwtUtil.getSessionId(claims);
            if (currentVersion == null || tokenSessionId == null || !currentVersion.equals(tokenSessionId)) {
                log.warn("Token 版本失效，userId={}", userId);
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            SysUserEntity authorizationState = userMapper.selectAuthorizationStateById(userId);
            Object tokenAuthVersion = claims.get("authVersion");
            long currentAuthVersion = authorizationState == null || authorizationState.getAuthVersion() == null
                    ? -1L : authorizationState.getAuthVersion();
            if (authorizationState == null
                    || !Byte.valueOf((byte) 1).equals(authorizationState.getStatus())
                    || Byte.valueOf((byte) 1).equals(authorizationState.getDelFlag())
                    || !(tokenAuthVersion instanceof Number)
                    || ((Number) tokenAuthVersion).longValue() != currentAuthVersion) {
                log.warn("Token 授权版本失效，userId={}", userId);
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userId, null,
                    authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException ex) {
            log.error("认证状态服务不可用: userId={}", userId, ex);
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), Result.error(ResultCode.SERVICE_UNAVAILABLE));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
        return bearer != null && bearer.startsWith("Bearer ") && bearer.length() > 7 ? bearer.substring(7) : null;
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
            if (!permissions.isBlank()) {
                java.util.stream.Stream.of(permissions.split(","))
                        .map(String::trim)
                        .filter(item -> !item.isEmpty())
                        .forEach(permissionList::add);
            }
        }
        return permissionList.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
