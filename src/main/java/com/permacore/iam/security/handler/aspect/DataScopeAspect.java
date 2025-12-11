package com.permacore.iam.security.aspect;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.permacore.iam.annotation.DataScope;
import com.permacore.iam.annotation.DataScopeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 数据权限切面
 * 自动在SQL中追加数据范围条件
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    /**
     * 定义切入点：所有使用@DataScope注解的方法
     */
    @Pointcut("@annotation(dataScope)")
    public void dataScopePointcut(DataScope dataScope) {}

    /**
     * 环绕通知：在方法执行前注入数据权限条件
     */
    @Around("dataScopePointcut(dataScope)")
    public Object around(ProceedingJoinPoint pjp, DataScope dataScope) throws Throwable {
        // 获取当前登录用户
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof User)) {
            return pjp.proceed();
        }

        Long userId = Long.parseLong(((User) principal).getUsername());

        // 获取方法参数
        Object[] args = pjp.getArgs();

        // 查找QueryWrapper参数
        Arrays.stream(args)
                .filter(arg -> arg instanceof QueryWrapper)
                .findFirst()
                .ifPresent(queryWrapper -> {
                    injectDataScope((QueryWrapper<?>) queryWrapper, dataScope, userId);
                });

        return pjp.proceed();
    }

    /**
     * 注入数据权限条件
     */
    private void injectDataScope(QueryWrapper<?> wrapper, DataScope dataScope, Long userId) {
        String deptAlias = dataScope.deptAlias();
        String prefix = StringUtils.hasText(deptAlias) ? deptAlias + "." : "";

        switch (dataScope.type()) {
            case DEPT_AND_CHILD:
                // 追加部门范围：AND (dept_id IN (当前部门及子部门))
                wrapper.apply(prefix + "dept_id IN (" +
                        "SELECT id FROM sys_dept WHERE dept_path LIKE CONCAT(" +
                        "'%', (SELECT dept_path FROM sys_dept WHERE id = " +
                        "(SELECT dept_id FROM sys_user WHERE id = {0}), '%'))", userId);
                break;

            case DEPT_ONLY:
                wrapper.eq(prefix + "dept_id",
                        "SELECT dept_id FROM sys_user WHERE id = " + userId);
                break;

            case SELF:
                wrapper.eq(prefix + "create_by", userId);
                break;

            case CUSTOM:
                if (StringUtils.hasText(dataScope.customCondition())) {
                    wrapper.apply(dataScope.customCondition());
                }
                break;

            case ALL:
            default:
                // 全部数据，不追加条件
                break;
        }

        log.debug("注入数据权限: userId={}, type={}", userId, dataScope.type());
    }
}