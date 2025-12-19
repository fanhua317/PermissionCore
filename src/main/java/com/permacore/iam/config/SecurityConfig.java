package com.permacore.iam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.security.handler.SecurityAccessDeniedHandler;
import com.permacore.iam.security.handler.SecurityAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import com.permacore.iam.service.UserService;
import com.permacore.iam.service.SysLoginLogService;
import com.permacore.iam.security.filter.JwtAuthenticationFilter;
import com.permacore.iam.security.filter.JwtAuthorizationOnceFilter;
import com.permacore.iam.utils.JwtUtil;
import com.permacore.iam.utils.RedisCacheUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
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

    private final UserService userService;
    private final SecurityAuthenticationEntryPoint authenticationEntryPoint;
    private final SecurityAccessDeniedHandler accessDeniedHandler;
    private final JwtUtil jwtUtil;
    private final RedisCacheUtil redisCacheUtil;
    private final ObjectMapper objectMapper;

    /**
     * 密码加密器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 强度10，耗时约500ms（推荐生产环境使用12）
        return new BCryptPasswordEncoder(10);
    }

    /**
     * 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        builder.authenticationProvider(authenticationProvider());
        return builder.build();
    }

    /**
     * 认证提供者（从数据库加载用户）
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService((org.springframework.security.core.userdetails.UserDetailsService) userService);
        provider.setPasswordEncoder(passwordEncoder());
        // 隐藏"UserNotFoundException"详细，防止用户名枚举攻击
        provider.setHideUserNotFoundExceptions(false);
        return provider;
    }

    /**
     * 注入 JwtAuthenticationFilter 为 Bean，以便在 filterChain 中使用
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(AuthenticationManager authenticationManager, SysLoginLogService loginLogService) {
        return new JwtAuthenticationFilter(authenticationManager, jwtUtil, redisCacheUtil, objectMapper, loginLogService);
    }

    /**
     * 注入 JwtAuthorizationOnceFilter 为 Bean
     */
    @Bean
    public JwtAuthorizationOnceFilter jwtAuthorizationOnceFilter() {
        return new JwtAuthorizationOnceFilter(jwtUtil, redisCacheUtil);
    }

    /**
     * 核心过滤器链配置
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                          JwtAuthenticationFilter jwtAuthenticationFilter,
                                          JwtAuthorizationOnceFilter jwtAuthorizationOnceFilter) throws Exception {
        http
                // 1. 禁用CSRF（JWT无状态，不需要CSRF）
                .csrf(AbstractHttpConfigurer::disable)

                // 2. CORS配置
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 3. 会话管理（无状态）
                .sessionManagement(session -> session
                        .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))

                // 4. 请求路径授权配置
                .authorizeHttpRequests(auth -> auth
                        // 白名单（无需认证）
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/captcha",
                                "/doc.html",
                                "/webjars/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/actuator/**",
                                "/test/**"
                        ).permitAll()
                        // 其他请求都需要认证
                        .anyRequest().authenticated()
                )

                // 5. 异常处理
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint) // 未登录
                        .accessDeniedHandler(accessDeniedHandler) // 权限不足
                )
                // 6. 添加JWT过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthorizationOnceFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS配置（支持前后端分离）
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*")); // 生产环境请配置具体域名
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true); // 允许携带Cookie
        configuration.setMaxAge(3600L); // 预检请求缓存1小时

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}