package com.permacore.iam.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.domain.vo.LoginVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.security.SecurityUser;
import com.permacore.iam.utils.JwtUtil;
import com.permacore.iam.utils.RedisCacheUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * JWT认证过滤器（处理登录请求）
 * 路径: POST /api/auth/login
 */
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RedisCacheUtil redisCacheUtil;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(AuthenticationManager authenticationManager,
                                   JwtUtil jwtUtil,
                                   RedisCacheUtil redisCacheUtil,
                                   ObjectMapper objectMapper) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.redisCacheUtil = redisCacheUtil;
        this.objectMapper = objectMapper;
        setRequiresAuthenticationRequestMatcher(new AntPathRequestMatcher("/api/auth/login", "POST"));
        super.setAuthenticationManager(authenticationManager);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {
        try {
            // 1. 解析请求体
            LoginVO loginVO = objectMapper.readValue(request.getInputStream(), LoginVO.class);
            log.info("用户登录尝试: username={}, password={}", loginVO.getUsername(), loginVO.getPassword()); // Debug log

            // 2. 创建认证令牌
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(
                            loginVO.getUsername(),
                            loginVO.getPassword()
                    );

            // 3. 执行认证（调用UserDetailsService）
            return authenticationManager.authenticate(token);

        } catch (IOException e) {
            log.error("登录请求解析失败", e);
            throw new RuntimeException("登录请求格式错误");
        }
    }

    /**
     * 认证成功后生成JWT
     */
    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain, Authentication authResult) throws IOException {
        SecurityUser securityUser = (SecurityUser) authResult.getPrincipal();
        Long userId = securityUser.getUserId();

        log.info("用户登录成功: userId={}", userId);

        // 1. 构建JWT载荷
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("permissions", securityUser.getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toList()));

        // 2. 生成Token
        String accessToken = jwtUtil.generateAccessToken(claims);
        String refreshToken = jwtUtil.generateRefreshToken(userId);

        // 3. 存储JWT版本号（用于强制登出）
        String jwtVersion = jwtUtil.getJtiFromToken(accessToken);
        redisCacheUtil.setJwtVersion(userId, jwtVersion,
                jwtUtil.getTokenRemainTime(accessToken), TimeUnit.SECONDS);

        // 4. 构建响应
        Map<String, Object> tokenMap = new HashMap<>();
        tokenMap.put("accessToken", accessToken);
        tokenMap.put("refreshToken", refreshToken);
        tokenMap.put("tokenType", "Bearer");
        tokenMap.put("expiresIn", jwtUtil.getTokenRemainTime(accessToken));

        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.success(tokenMap)));
    }

    /**
     * 认证失败处理
     */
    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                              AuthenticationException failed) throws IOException {
        log.warn("登录失败: {}", failed.getMessage());

        Result<Void> result;
        if (failed instanceof BadCredentialsException) {
            result = Result.error("用户名或密码错误");
        } else if (failed instanceof DisabledException) {
            result = Result.error("用户已被禁用");
        } else {
            result = Result.error("登录失败：" + failed.getMessage());
        }

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
