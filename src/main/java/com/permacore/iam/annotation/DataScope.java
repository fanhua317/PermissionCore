package com.permacore.iam.annotation;

import java.lang.annotation.*;

/**
 * 数据权限注解
 * 用于控制用户能访问的数据范围
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {
    /**
     * 部门字段别名
     */
    String deptAlias() default "";

    /**
     * 数据权限类型
     */
    com.permacore.iam.annotation.DataScopeType type() default com.permacore.iam.annotation.DataScopeType.DEPT_AND_CHILD;

    /**
     * 自定义条件
     */
    String customCondition() default "";
}
