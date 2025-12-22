package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.permacore.iam.domain.entity.SysSodConstraintEntity;
import com.permacore.iam.domain.vo.PageVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.service.SysSodConstraintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 职责分离约束表 前端控制器
 * RBAC3 核心特性之一：支持静态互斥(SSD)和动态互斥(DSD)约束
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Tag(name = "SoD职责分离", description = "RBAC3职责分离约束管理")
@RestController
@RequestMapping("/api/sod-constraint")
@RequiredArgsConstructor
public class SysSodConstraintController {

    private static final Logger log = LoggerFactory.getLogger(SysSodConstraintController.class);
    
    private final SysSodConstraintService sodConstraintService;

    /**
     * 获取所有 SoD 约束列表
     */
    @Operation(summary = "获取所有约束", description = "获取所有SoD约束列表")
    @GetMapping("/list")
    public Result<List<SysSodConstraintEntity>> list() {
        List<SysSodConstraintEntity> constraints = sodConstraintService.list(
            new LambdaQueryWrapper<SysSodConstraintEntity>()
                .orderByDesc(SysSodConstraintEntity::getCreateTime)
        );
        return Result.success(constraints);
    }

    /**
     * 分页查询 SoD 约束
     */
    @Operation(summary = "分页查询约束", description = "根据条件分页查询SoD约束")
    @GetMapping("/page")
    public Result<PageVO<SysSodConstraintEntity>> page(
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(name = "constraintName", required = false) String constraintName,
            @RequestParam(name = "constraintType", required = false) Byte constraintType) {
        
        Page<SysSodConstraintEntity> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SysSodConstraintEntity> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(constraintName)) {
            wrapper.like(SysSodConstraintEntity::getConstraintName, constraintName);
        }
        if (constraintType != null) {
            wrapper.eq(SysSodConstraintEntity::getConstraintType, constraintType);
        }
        
        wrapper.orderByDesc(SysSodConstraintEntity::getCreateTime);
        
        Page<SysSodConstraintEntity> result = sodConstraintService.page(page, wrapper);
        return Result.success(PageVO.of(result));
    }

    /**
     * 获取约束详情
     */
    @Operation(summary = "获取约束详情", description = "根据ID获取SoD约束详情")
    @GetMapping("/{id}")
    public Result<SysSodConstraintEntity> getById(@PathVariable Long id) {
        SysSodConstraintEntity constraint = sodConstraintService.getById(id);
        return Result.success(constraint);
    }

    /**
     * 创建 SoD 约束
     */
    @Operation(summary = "创建约束", description = "新增SoD约束")
    @PreAuthorize("hasAuthority('role:edit')")
    @PostMapping
    public Result<Void> create(@RequestBody SysSodConstraintEntity constraint) {
        constraint.setCreateTime(LocalDateTime.now());
        sodConstraintService.save(constraint);
        log.info("创建SoD约束: {}", constraint.getConstraintName());
        return Result.success();
    }

    /**
     * 更新 SoD 约束
     */
    @Operation(summary = "更新约束", description = "更新SoD约束信息")
    @PreAuthorize("hasAuthority('role:edit')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysSodConstraintEntity constraint) {
        constraint.setId(id);
        sodConstraintService.updateById(constraint);
        log.info("更新SoD约束: id={}", id);
        return Result.success();
    }

    /**
     * 删除 SoD 约束
     */
    @Operation(summary = "删除约束", description = "删除SoD约束")
    @PreAuthorize("hasAuthority('role:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sodConstraintService.removeById(id);
        log.info("删除SoD约束: id={}", id);
        return Result.success();
    }
}
