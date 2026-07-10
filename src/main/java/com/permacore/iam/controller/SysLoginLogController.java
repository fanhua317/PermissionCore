package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.permacore.iam.domain.entity.SysLoginLogEntity;
import com.permacore.iam.domain.vo.PageVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.service.SysLoginLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 登录日志控制器
 */
@Tag(name = "登录日志", description = "系统登录日志查询与管理")
@RestController
@RequestMapping("/api/login-log")
@RequiredArgsConstructor
@Validated
public class SysLoginLogController {

    private static final Logger log = LoggerFactory.getLogger(SysLoginLogController.class);

    private final SysLoginLogService loginLogService;

    /**
     * 分页查询登录日志
     */
    @Operation(summary = "分页查询", description = "根据条件分页查询登录日志")
    @PreAuthorize("hasAuthority('system:log:query')")
    @GetMapping("/page")
    public Result<PageVO<SysLoginLogEntity>> page(
            @RequestParam(name = "pageNo", defaultValue = "1")
            @Min(value = 1, message = "pageNo不能小于1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10")
            @Min(value = 1, message = "pageSize不能小于1")
            @Max(value = 100, message = "pageSize不能超过100") Integer pageSize,
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime) {

        Page<SysLoginLogEntity> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SysLoginLogEntity> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(username)) {
            wrapper.like(SysLoginLogEntity::getUsername, username);
        }
        if (status != null) {
            wrapper.eq(SysLoginLogEntity::getStatus, status);
        }
        if (StringUtils.hasText(startTime)) {
            LocalDate date = LocalDate.parse(startTime);
            wrapper.ge(SysLoginLogEntity::getLoginTime, date.atStartOfDay());
        }
        if (StringUtils.hasText(endTime)) {
            LocalDate date = LocalDate.parse(endTime);
            wrapper.le(SysLoginLogEntity::getLoginTime, date.atTime(LocalTime.MAX));
        }

        wrapper.orderByDesc(SysLoginLogEntity::getLoginTime, SysLoginLogEntity::getId);

        Page<SysLoginLogEntity> result = loginLogService.page(page, wrapper);

        return Result.success(PageVO.of(result));
    }

    /**
     * 获取登录日志详情
     */
    @Operation(summary = "获取详情", description = "根据ID获取登录日志详情")
    @PreAuthorize("hasAuthority('system:log:query')")
    @GetMapping("/{id}")
    public Result<SysLoginLogEntity> getById(@PathVariable Long id) {
        SysLoginLogEntity logEntity = loginLogService.getById(id);
        return Result.success(logEntity);
    }

    /**
     * 清空登录日志
     */
    @Operation(summary = "清空日志", description = "清空所有登录日志")
    @PreAuthorize("hasAuthority('log:delete')")
    @DeleteMapping("/clear")
    public Result<Void> clear() {
        loginLogService.remove(new LambdaQueryWrapper<>());
        log.info("清空登录日志");
        return Result.success();
    }

    /**
     * 删除单条登录日志
     */
    @Operation(summary = "删除日志", description = "删除单条登录日志")
    @PreAuthorize("hasAuthority('log:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        loginLogService.removeById(id);
        return Result.success();
    }
}
