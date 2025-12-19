package com.permacore.iam.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.annotation.OperLog;
import com.permacore.iam.domain.entity.SysOperLogEntity;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.service.SysOperLogService;
import com.permacore.iam.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 操作日志AOP切面
 */
@Aspect
@Component
public class OperLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperLogAspect.class);
    
    private final SysOperLogService operLogService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final SysUserMapper sysUserMapper;
    
    public OperLogAspect(SysOperLogService operLogService, JwtUtil jwtUtil, ObjectMapper objectMapper, SysUserMapper sysUserMapper) {
        this.operLogService = operLogService;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.sysUserMapper = sysUserMapper;
    }
    
    /**
     * 定义切点 - 所有带有@OperLog注解的方法
     */
    @Pointcut("@annotation(com.permacore.iam.annotation.OperLog)")
    public void operLogPointCut() {
    }
    
    /**
     * 方法正常返回后记录日志
     */
    @AfterReturning(pointcut = "operLogPointCut()", returning = "result")
    public void doAfterReturning(JoinPoint joinPoint, Object result) {
        handleLog(joinPoint, null, result);
    }
    
    /**
     * 方法抛出异常后记录日志
     */
    @AfterThrowing(pointcut = "operLogPointCut()", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, Exception e) {
        handleLog(joinPoint, e, null);
    }
    
    /**
     * 处理日志记录
     */
    private void handleLog(JoinPoint joinPoint, Exception e, Object result) {
        try {
            // 获取注解
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            OperLog operLog = method.getAnnotation(OperLog.class);
            
            if (operLog == null) {
                return;
            }
            
            // 获取请求信息
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            HttpServletRequest request = attributes.getRequest();
            
            // 构建日志实体
            SysOperLogEntity logEntity = new SysOperLogEntity();
            logEntity.setTitle(operLog.title());
            logEntity.setBusinessType((byte) operLog.businessType());
            logEntity.setMethod(joinPoint.getTarget().getClass().getName() + "." + method.getName() + "()");
            logEntity.setRequestMethod(request.getMethod());
            logEntity.setOperIp(getClientIp(request));
            logEntity.setOperLocation("本地");
            logEntity.setOperTime(LocalDateTime.now());
            
            // 获取操作人信息 - 优先从 SecurityContext 获取
            try {
                org.springframework.security.core.Authentication auth = 
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                    Object principal = auth.getPrincipal();
                    if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                        String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
                        logEntity.setOperatorName(username);
                    } else if (principal instanceof String) {
                        logEntity.setOperatorName((String) principal);
                    } else if (principal instanceof Number) {
                        Long userId = ((Number) principal).longValue();
                        logEntity.setOperatorId(userId);

                        // 先用 token 的 username（更快），没有再查库
                        String token = resolveToken(request);
                        if (token != null) {
                            try {
                                String username = jwtUtil.getUsernameFromToken(token);
                                if (username != null && !username.isBlank()) {
                                    logEntity.setOperatorName(username);
                                }
                            } catch (Exception ex) {
                                log.debug("从Token获取用户名失败: {}", ex.getMessage());
                            }
                        }
                        if (logEntity.getOperatorName() == null || logEntity.getOperatorName().isEmpty()) {
                            SysUserEntity user = sysUserMapper.selectById(userId);
                            if (user != null) {
                                logEntity.setOperatorName(user.getUsername());
                            }
                        }
                    } else {
                        // 兜底：至少写入 auth.getName()
                        String name = auth.getName();
                        if (name != null && !name.isBlank()) {
                            logEntity.setOperatorName(name);
                        }
                    }
                }
            } catch (Exception ex) {
                log.debug("从SecurityContext获取用户信息失败: {}", ex.getMessage());
            }
            
            // 如果 SecurityContext 没有获取到，尝试从 token 获取
            if (logEntity.getOperatorName() == null || logEntity.getOperatorName().isEmpty()) {
                String token = resolveToken(request);
                if (token != null) {
                    try {
                        Long userId = jwtUtil.getUserIdFromToken(token);
                        String username = jwtUtil.getUsernameFromToken(token);
                        logEntity.setOperatorId(userId);
                        if (username != null) {
                            logEntity.setOperatorName(username);
                        }
                    } catch (Exception ex) {
                        log.debug("从Token获取用户信息失败: {}", ex.getMessage());
                    }
                }
            }

            // 最后兜底：只有 operatorId 没有 operatorName 时，查库补齐
            if ((logEntity.getOperatorName() == null || logEntity.getOperatorName().isEmpty()) && logEntity.getOperatorId() != null) {
                try {
                    SysUserEntity user = sysUserMapper.selectById(logEntity.getOperatorId());
                    if (user != null) {
                        logEntity.setOperatorName(user.getUsername());
                    }
                } catch (Exception ex) {
                    log.debug("查库补齐操作人失败: {}", ex.getMessage());
                }
            }
            
            // 保存请求参数
            if (operLog.isSaveRequestData()) {
                try {
                    Object[] args = joinPoint.getArgs();
                    String params = objectMapper.writeValueAsString(args);
                    if (params.length() > 2000) {
                        params = params.substring(0, 2000) + "...";
                    }
                    logEntity.setOperParam(params);
                } catch (Exception ex) {
                    log.debug("序列化请求参数失败: {}", ex.getMessage());
                }
            }
            
            // 保存响应数据
            if (operLog.isSaveResponseData() && result != null) {
                try {
                    String jsonResult = objectMapper.writeValueAsString(result);
                    if (jsonResult.length() > 2000) {
                        jsonResult = jsonResult.substring(0, 2000) + "...";
                    }
                    logEntity.setJsonResult(jsonResult);
                } catch (Exception ex) {
                    log.debug("序列化响应数据失败: {}", ex.getMessage());
                }
            }
            
            // 设置状态
            if (e != null) {
                logEntity.setStatus((byte) 1); // 异常
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.length() > 2000) {
                    errorMsg = errorMsg.substring(0, 2000);
                }
                logEntity.setErrorMsg(errorMsg);
            } else {
                logEntity.setStatus((byte) 0); // 正常
            }
            
            // 保存日志
            operLogService.save(logEntity);
            
        } catch (Exception ex) {
            log.error("记录操作日志失败: {}", ex.getMessage());
        }
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
    
    /**
     * 解析Token
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
