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
    DataScopeType type() default DataScopeType.DEPT_AND_CHILD;

    /**
     * 自定义条件
     */
    String customCondition() default "";
}

/**
 * 数据权限类型枚举
 */
enum DataScopeType {
    /**
     * 全部数据（仅超级管理员）
     */
    ALL,

    /**
     * 当前部门及子部门
     */
    DEPT_AND_CHILD,

    /**
     * 仅当前部门
     */
    DEPT_ONLY,

    /**
     * 仅自己创建的数据
     */
    SELF,

    /**
     * 自定义
     */
    CUSTOM
}