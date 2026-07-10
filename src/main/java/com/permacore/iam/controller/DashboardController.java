package com.permacore.iam.controller;

import com.permacore.iam.domain.entity.SysLoginLogEntity;
import com.permacore.iam.domain.entity.SysOperLogEntity;
import com.permacore.iam.domain.vo.DashboardStatsVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.mapper.DashboardMapper;
import com.permacore.iam.mapper.SysLoginLogMapper;
import com.permacore.iam.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard 仪表盘控制器
 * 提供系统统计数据和概览信息
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private static final int RECENT_LOG_LIMIT = 5;

    private final DashboardMapper dashboardMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final SysOperLogMapper operLogMapper;

    /**
     * 获取统计数据
     */
    @PreAuthorize("hasAnyAuthority('admin:*','system:user:query','system:role:query','system:permission:query','system:dept:query','system:log:query')")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        DashboardStatsVO aggregate = dashboardMapper.selectStats(todayStart);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("userCount", aggregate.getUserCount());
        stats.put("roleCount", aggregate.getRoleCount());
        stats.put("permissionCount", aggregate.getPermissionCount());
        stats.put("deptCount", aggregate.getDeptCount());
        stats.put("todayLoginCount", aggregate.getTodayLoginCount());
        stats.put("todayOperCount", aggregate.getTodayOperCount());

        return Result.success(stats);
    }

    /**
     * 获取最近登录日志（5条）
     */
    @PreAuthorize("hasAnyAuthority('admin:*','system:log:query')")
    @GetMapping("/recent-logins")
    public Result<List<SysLoginLogEntity>> getRecentLogins() {
        return Result.success(loginLogMapper.selectRecent(RECENT_LOG_LIMIT));
    }

    /**
     * 获取最近操作日志（5条）
     */
    @PreAuthorize("hasAnyAuthority('admin:*','system:log:query')")
    @GetMapping("/recent-operations")
    public Result<List<SysOperLogEntity>> getRecentOperations() {
        return Result.success(operLogMapper.selectRecent(RECENT_LOG_LIMIT));
    }

    /**
     * 获取系统信息
     */
    @PreAuthorize("hasAuthority('admin:*')")
    @GetMapping("/system-info")
    public Result<Map<String, Object>> getSystemInfo() {
        Map<String, Object> info = new LinkedHashMap<>();

        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        info.put("javaVersion", System.getProperty("java.version"));
        info.put("osName", System.getProperty("os.name"));
        info.put("osArch", System.getProperty("os.arch"));
        info.put("totalMemory", totalMemory + " MB");
        info.put("usedMemory", usedMemory + " MB");
        info.put("freeMemory", freeMemory + " MB");
        info.put("processors", runtime.availableProcessors());
        info.put("serverTime", LocalDateTime.now().toString());

        return Result.success(info);
    }
}
