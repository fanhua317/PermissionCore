package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.permacore.iam.domain.entity.*;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dashboard 仪表盘控制器
 * 提供系统统计数据和概览信息
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final UserService userService;
    private final SysRoleService roleService;
    private final SysPermissionService permissionService;
    private final SysDeptService deptService;
    private final SysLoginLogService loginLogService;
    private final SysOperLogService operLogService;

    /**
     * 获取统计数据
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 用户总数（排除已删除）
        LambdaQueryWrapper<SysUserEntity> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUserEntity::getDelFlag, (byte) 0);
        stats.put("userCount", userService.count(userWrapper));
        
        // 角色总数
        stats.put("roleCount", roleService.count());
        
        // 权限总数
        stats.put("permissionCount", permissionService.count());
        
        // 部门总数
        stats.put("deptCount", deptService.count());
        
        // 今日登录次数
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        LambdaQueryWrapper<SysLoginLogEntity> loginWrapper = new LambdaQueryWrapper<>();
        loginWrapper.ge(SysLoginLogEntity::getLoginTime, todayStart);
        stats.put("todayLoginCount", loginLogService.count(loginWrapper));
        
        // 今日操作次数
        LambdaQueryWrapper<SysOperLogEntity> operWrapper = new LambdaQueryWrapper<>();
        operWrapper.ge(SysOperLogEntity::getOperTime, todayStart);
        stats.put("todayOperCount", operLogService.count(operWrapper));
        
        return Result.success(stats);
    }

    /**
     * 获取最近登录日志（5条）
     */
    @GetMapping("/recent-logins")
    public Result<List<SysLoginLogEntity>> getRecentLogins() {
        LambdaQueryWrapper<SysLoginLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SysLoginLogEntity::getLoginTime);
        Page<SysLoginLogEntity> page = loginLogService.page(new Page<>(1, 5), wrapper);
        return Result.success(page.getRecords());
    }

    /**
     * 获取最近操作日志（5条）
     */
    @GetMapping("/recent-operations")
    public Result<List<SysOperLogEntity>> getRecentOperations() {
        LambdaQueryWrapper<SysOperLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SysOperLogEntity::getOperTime);
        Page<SysOperLogEntity> page = operLogService.page(new Page<>(1, 5), wrapper);
        return Result.success(page.getRecords());
    }

    /**
     * 获取系统信息
     */
    @GetMapping("/system-info")
    public Result<Map<String, Object>> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        
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
