package com.permacore.iam.annotation;

/**
 * 数据权限类型枚举
 */
public enum DataScopeType {
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

