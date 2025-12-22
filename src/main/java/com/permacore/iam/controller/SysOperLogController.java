package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.permacore.iam.domain.entity.SysOperLogEntity;
import com.permacore.iam.domain.vo.PageVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.service.SysOperLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 操作日志控制器
 */
@Tag(name = "操作日志", description = "系统操作日志查询与管理")
@RestController
@RequestMapping("/api/oper-log")
@RequiredArgsConstructor
public class SysOperLogController {

    private static final Logger log = LoggerFactory.getLogger(SysOperLogController.class);

    private final SysOperLogService operLogService;

    /**
     * 分页查询操作日志
     */
    @Operation(summary = "分页查询", description = "根据条件分页查询操作日志")
    @GetMapping("/page")
    public Result<PageVO<SysOperLogEntity>> page(
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(name = "operatorName", required = false) String operatorName,
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime) {
        
        Page<SysOperLogEntity> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SysOperLogEntity> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(operatorName)) {
            wrapper.like(SysOperLogEntity::getOperatorName, operatorName);
        }
        if (StringUtils.hasText(title)) {
            wrapper.like(SysOperLogEntity::getTitle, title);
        }
        if (status != null) {
            wrapper.eq(SysOperLogEntity::getStatus, status);
        }
        if (StringUtils.hasText(startTime)) {
            LocalDate date = LocalDate.parse(startTime);
            wrapper.ge(SysOperLogEntity::getOperTime, date.atStartOfDay());
        }
        if (StringUtils.hasText(endTime)) {
            LocalDate date = LocalDate.parse(endTime);
            wrapper.le(SysOperLogEntity::getOperTime, date.atTime(LocalTime.MAX));
        }
        
        wrapper.orderByDesc(SysOperLogEntity::getOperTime);
        
        Page<SysOperLogEntity> result = operLogService.page(page, wrapper);
        return Result.success(PageVO.of(result));
    }

    /**
     * 获取操作日志详情
     */
    @Operation(summary = "获取详情", description = "根据ID获取操作日志详情")
    @GetMapping("/{id}")
    public Result<SysOperLogEntity> getById(@PathVariable Long id) {
        SysOperLogEntity logEntity = operLogService.getById(id);
        return Result.success(logEntity);
    }

    /**
     * 清空操作日志
     */
    @Operation(summary = "清空日志", description = "清空所有操作日志")
    @PreAuthorize("hasAuthority('log:delete')")
    @DeleteMapping("/clear")
    public Result<Void> clear() {
        operLogService.remove(new LambdaQueryWrapper<>());
        log.info("清空操作日志");
        return Result.success();
    }

    /**
     * 删除单条操作日志
     */
    @Operation(summary = "删除日志", description = "删除单条操作日志")
    @PreAuthorize("hasAuthority('log:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        operLogService.removeById(id);
        return Result.success();
    }
}
