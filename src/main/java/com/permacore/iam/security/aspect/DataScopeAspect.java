package com.permacore.iam.security.aspect;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.permacore.iam.annotation.DataScope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 数据权限切面
 * 自动在SQL中追加数据范围条件
 */
@Aspect
@Component
public class DataScopeAspect {

    private static final Logger log = LoggerFactory.getLogger(DataScopeAspect.class);

    /**
     * 定义切入点：所有使用@DataScope注解的方法
     */
    @Pointcut("@annotation(com.permacore.iam.annotation.DataScope)")
    public void dataScopePointcut(DataScope dataScope) {}

    /**
     * 环绕通知：在方法执行前注入数据权限条件
     */
    @Around("dataScopePointcut(dataScope)")
    public Object around(ProceedingJoinPoint pjp, DataScope dataScope) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return pjp.proceed();
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof User)) {
            return pjp.proceed();
        }

        Long userId = parseUserId((User) principal);
        if (userId == null) {
            return pjp.proceed();
        }

        AbstractWrapper<?, ?, ?> targetWrapper = null;
        for (Object arg : pjp.getArgs()) {
            if (arg instanceof AbstractWrapper) {
                targetWrapper = (AbstractWrapper<?, ?, ?>) arg;
                break;
            }
        }

        if (targetWrapper != null) {
            injectDataScope(targetWrapper, dataScope, userId);
        }

        return pjp.proceed();
    }

    /**
     * 注入数据权限条件
     */
    private void injectDataScope(AbstractWrapper<?, ?, ?> wrapper, DataScope dataScope, Long userId) {
        String deptAlias = dataScope.deptAlias();
        String prefix = StringUtils.hasText(deptAlias) ? deptAlias + "." : "";

        switch (dataScope.type()) {
            case DEPT_AND_CHILD:
                wrapper.apply(prefix + "dept_id IN (" +
                        "SELECT child.id FROM sys_dept child " +
                        "JOIN sys_dept parent ON child.dept_path LIKE CONCAT(parent.dept_path, '%') " +
                        "WHERE parent.id = (SELECT dept_id FROM sys_user WHERE id = {0}))", userId);
                break;

            case DEPT_ONLY:
                wrapper.apply(prefix + "dept_id = (SELECT dept_id FROM sys_user WHERE id = {0})", userId);
                break;

            case SELF:
                wrapper.apply(prefix + "create_by = {0}", userId);
                break;

            case CUSTOM:
                if (StringUtils.hasText(dataScope.customCondition())) {
                    wrapper.apply(dataScope.customCondition(), userId);
                }
                break;

            case ALL:
            default:
                break;
        }

        log.debug("注入数据权限: userId={}, type={}", userId, dataScope.type());
    }

    private Long parseUserId(User principal) {
        try {
            return Long.parseLong(principal.getUsername());
        } catch (NumberFormatException ex) {
            log.warn("当前登录用户标识无法解析为数字，跳过数据权限: username={}", principal.getUsername());
            return null;
        }
    }
}
