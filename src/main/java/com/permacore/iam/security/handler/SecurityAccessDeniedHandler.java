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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 访问拒绝处理器
 * 职责：处理已登录但权限不足的情况，返回403状态码
 *
 * @Component 注册为Spring Bean，由SecurityConfig配置使用
 */
@Component
@RequiredArgsConstructor
public class SecurityAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(SecurityAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        // 记录安全日志：权限不足
        log.warn("权限不足: uri={}, user={}, error={}",
                request.getRequestURI(),
                request.getUserPrincipal(),
                accessDeniedException.getMessage());

        // 设置响应格式和状态码
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        // 返回统一响应格式
        String json = objectMapper.writeValueAsString(
                Result.error(ResultCode.FORBIDDEN)
        );
        response.getWriter().write(json);
    }
}

/*
 * 非 Lombok 版本示例：
 * @Component
 * public class SecurityAccessDeniedHandler implements AccessDeniedHandler {
 *     private static final Logger log = LoggerFactory.getLogger(SecurityAccessDeniedHandler.class);
 *     private final ObjectMapper objectMapper;
 *
 *     public SecurityAccessDeniedHandler(ObjectMapper objectMapper) {
 *         this.objectMapper = objectMapper;
 *     }
 *     // 其余方法保持不变
 * }
 */
