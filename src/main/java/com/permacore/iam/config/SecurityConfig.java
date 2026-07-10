package com.permacore.iam.config;

import com.permacore.iam.security.handler.SecurityAccessDeniedHandler;
import com.permacore.iam.security.handler.SecurityAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import com.permacore.iam.security.filter.JwtAuthorizationOnceFilter;
import com.permacore.iam.utils.JwtUtil;
import com.permacore.iam.utils.RedisCacheUtil;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.mapper.SysPermissionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Spring Security 6 核心配置
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true) // 启用方法级注解
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityAuthenticationEntryPoint authenticationEntryPoint;
    private final SecurityAccessDeniedHandler accessDeniedHandler;
    private final JwtUtil jwtUtil;
    private final RedisCacheUtil redisCacheUtil;
    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @org.springframework.beans.factory.annotation.Value("${app.security.public-docs:false}")
    private boolean publicDocs;

    @org.springframework.beans.factory.annotation.Value("${app.security.public-metrics:false}")
    private boolean publicMetrics;

    /**
     * 密码加密器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 强度10，耗时约500ms（推荐生产环境使用12）
        return new BCryptPasswordEncoder(10);
    }

    /**
     * 注入 JwtAuthorizationOnceFilter 为 Bean
     */
    @Bean
    public JwtAuthorizationOnceFilter jwtAuthorizationOnceFilter() {
        return new JwtAuthorizationOnceFilter(jwtUtil, redisCacheUtil, userMapper, permissionMapper, objectMapper);
    }

    /**
     * 核心过滤器链配置
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            JwtAuthorizationOnceFilter jwtAuthorizationOnceFilter) throws Exception {
        http
                // 1. 禁用CSRF（JWT无状态，不需要CSRF）
                .csrf(AbstractHttpConfigurer::disable)

                // 2. CORS配置
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 3. 会话管理（无状态）
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                org.springframework.security.config.http.SessionCreationPolicy.STATELESS))

                // 4. 请求路径授权配置
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/api/health",
                            "/api/auth/login",
                            "/api/auth/refresh",
                            "/uploads/avatars/**")
                            .permitAll();
                    if (publicDocs) {
                        auth.requestMatchers(
                                "/doc.html",
                                "/webjars/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**")
                                .permitAll();
                    }
                    if (publicMetrics) {
                        auth.requestMatchers("/actuator/prometheus").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })

                // 5. 异常处理
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint) // 未登录
                        .accessDeniedHandler(accessDeniedHandler) // 权限不足
                )
                // 6. 添加JWT授权过滤器；登录由 AuthController 单一实现
                .addFilterBefore(jwtAuthorizationOnceFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS配置（支持前后端分离）
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(false); // 系统仅使用 Authorization Bearer Token
        configuration.setMaxAge(3600L); // 预检请求缓存1小时

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
