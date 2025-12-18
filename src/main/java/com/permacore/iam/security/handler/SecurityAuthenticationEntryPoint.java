package com.permacore.iam.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.domain.vo.ResultCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 认证入口点处理器
 * 职责：处理未登录或Token失效的请求，返回401状态码
 *
 * @Component 注册为Spring Bean，由SecurityConfig配置使用
 */
@Component
@RequiredArgsConstructor
public class SecurityAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        // 记录安全日志：未登录访问
        log.warn("未登录访问受保护资源: uri={}, error={}",
                request.getRequestURI(),
                authException.getMessage());

        // 设置响应格式和状态码
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // 返回统一响应格式
        String json = objectMapper.writeValueAsString(
                Result.error(ResultCode.UNAUTHORIZED)
        );
        response.getWriter().write(json);
    }
}

/*
 * 非 Lombok 版本示例：
 * @Component
 * public class SecurityAuthenticationEntryPoint implements AuthenticationEntryPoint {
 *     private static final Logger log = LoggerFactory.getLogger(SecurityAuthenticationEntryPoint.class);
 *     private final ObjectMapper objectMapper;
 *
 *     public SecurityAuthenticationEntryPoint(ObjectMapper objectMapper) {
 *         this.objectMapper = objectMapper;
 *     }
 *     // 其余方法保持不变
 * }
 */
