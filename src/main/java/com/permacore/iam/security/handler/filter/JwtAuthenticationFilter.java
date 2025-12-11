package com.permacore.iam.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.domain.entity.UserEntity;
import com.permacore.iam.domain.vo.LoginVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.utils.JwtUtil;
import com.permacore.iam.utils.RedisCacheUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JWT认证过滤器（处理登录请求）
 * 路径: POST /api/auth/login
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RedisCacheUtil redisCacheUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthenticationFilter() {
        // 设置登录路径
        setRequiresAuthenticationRequestMatcher(
                new AntPathRequestMatcher("/api/auth/login", "POST")
        );
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {
        try {
            // 1. 解析请求体
            LoginVO loginVO = objectMapper.readValue(request.getInputStream(), LoginVO.class);
            log.info("用户登录尝试: username={}", loginVO.getUsername());

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
        User securityUser = (User) authResult.getPrincipal();
        Long userId = Long.parseLong(securityUser.getUsername()); // username存的是用户ID

        log.info("用户登录成功: userId={}", userId);

        // 1. 构建JWT载荷
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("permissions", securityUser.getAuthorities().stream()
                .map(Object::toString)
                .toArray());

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