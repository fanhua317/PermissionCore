package com.permacore.iam.domain.vo;

/**
 * 仪表盘统计聚合查询结果。
 */
public class DashboardStatsVO {
    private long userCount;
    private long roleCount;
    private long permissionCount;
    private long deptCount;
    private long todayLoginCount;
    private long todayOperCount;

    public long getUserCount() {
        return userCount;
    }

    public void setUserCount(long userCount) {
        this.userCount = userCount;
    }

    public long getRoleCount() {
        return roleCount;
    }

    public void setRoleCount(long roleCount) {
        this.roleCount = roleCount;
    }

    public long getPermissionCount() {
        return permissionCount;
    }

    public void setPermissionCount(long permissionCount) {
        this.permissionCount = permissionCount;
    }

    public long getDeptCount() {
        return deptCount;
    }

    public void setDeptCount(long deptCount) {
        this.deptCount = deptCount;
    }

    public long getTodayLoginCount() {
        return todayLoginCount;
    }

    public void setTodayLoginCount(long todayLoginCount) {
        this.todayLoginCount = todayLoginCount;
    }

    public long getTodayOperCount() {
        return todayOperCount;
    }

    public void setTodayOperCount(long todayOperCount) {
        this.todayOperCount = todayOperCount;
    }
}
