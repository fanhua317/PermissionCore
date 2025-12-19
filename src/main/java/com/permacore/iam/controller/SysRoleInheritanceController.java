package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.permacore.iam.domain.entity.SysRoleEntity;
import com.permacore.iam.domain.entity.SysRoleInheritanceEntity;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.domain.vo.RoleInheritanceVO;
import com.permacore.iam.domain.vo.RoleVO;
import com.permacore.iam.service.SysRoleInheritanceService;
import com.permacore.iam.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色继承关系控制器
 */
@RestController
@RequestMapping("/api/role-inheritance")
@RequiredArgsConstructor
public class SysRoleInheritanceController {

    private static final Logger log = LoggerFactory.getLogger(SysRoleInheritanceController.class);

    private final SysRoleInheritanceService inheritanceService;
    private final SysRoleService roleService;

    /**
     * 获取角色的父角色列表
     */
    @GetMapping("/parents/{roleId}")
    public Result<List<RoleVO>> getParentRoles(@PathVariable Long roleId) {
        // 查询当前角色的所有直接父角色（depth=1）
        List<SysRoleInheritanceEntity> inheritances = inheritanceService.list(
            new LambdaQueryWrapper<SysRoleInheritanceEntity>()
                .eq(SysRoleInheritanceEntity::getDescendantId, roleId)
                .eq(SysRoleInheritanceEntity::getDepth, 1)
        );
        
        List<Long> parentIds = inheritances.stream()
            .map(SysRoleInheritanceEntity::getAncestorId)
            .collect(Collectors.toList());
        
        if (parentIds.isEmpty()) {
            return Result.success(List.of());
        }
        
        List<SysRoleEntity> parentRoles = roleService.listByIds(parentIds);
        List<RoleVO> result = parentRoles.stream()
            .map(r -> {
                RoleVO vo = new RoleVO();
                vo.setId(r.getId());
                vo.setRoleCode(r.getRoleKey());
                vo.setRoleName(r.getRoleName());
                return vo;
            })
            .collect(Collectors.toList());
        
        return Result.success(result);
    }

    /**
     * 获取角色的子角色列表
     */
    @GetMapping("/children/{roleId}")
    public Result<List<RoleVO>> getChildRoles(@PathVariable Long roleId) {
        // 查询当前角色的所有直接子角色（depth=1）
        List<SysRoleInheritanceEntity> inheritances = inheritanceService.list(
            new LambdaQueryWrapper<SysRoleInheritanceEntity>()
                .eq(SysRoleInheritanceEntity::getAncestorId, roleId)
                .eq(SysRoleInheritanceEntity::getDepth, 1)
        );
        
        List<Long> childIds = inheritances.stream()
            .map(SysRoleInheritanceEntity::getDescendantId)
            .collect(Collectors.toList());
        
        if (childIds.isEmpty()) {
            return Result.success(List.of());
        }
        
        List<SysRoleEntity> childRoles = roleService.listByIds(childIds);
        List<RoleVO> result = childRoles.stream()
            .map(r -> {
                RoleVO vo = new RoleVO();
                vo.setId(r.getId());
                vo.setRoleCode(r.getRoleKey());
                vo.setRoleName(r.getRoleName());
                return vo;
            })
            .collect(Collectors.toList());
        
        return Result.success(result);
    }

    /**
     * 更新角色的继承关系（设置父角色）
     */
    @PreAuthorize("hasAuthority('role:setInheritance')")
    @PutMapping("/{roleId}")
    public Result<Void> updateInheritance(@PathVariable Long roleId, @RequestBody RoleInheritanceVO vo) {
        // 删除当前角色的所有父角色关系
        inheritanceService.remove(
            new LambdaQueryWrapper<SysRoleInheritanceEntity>()
                .eq(SysRoleInheritanceEntity::getDescendantId, roleId)
        );
        
        // 添加新的父角色关系
        if (vo.getParentRoleIds() != null && !vo.getParentRoleIds().isEmpty()) {
            for (Long parentId : vo.getParentRoleIds()) {
                SysRoleInheritanceEntity inheritance = new SysRoleInheritanceEntity();
                inheritance.setAncestorId(parentId);
                inheritance.setDescendantId(roleId);
                inheritance.setDepth(1);
                inheritanceService.save(inheritance);
            }
        }
        
        log.info("更新角色继承: roleId={}, parents={}", roleId, vo.getParentRoleIds());
        return Result.success();
    }

    /**
     * 添加单个继承关系
     */
    @PreAuthorize("hasAuthority('role:setInheritance')")
    @PostMapping("/{childId}/parent/{parentId}")
    public Result<Void> addInheritance(@PathVariable Long childId, @PathVariable Long parentId) {
        // 检查是否已存在
        long count = inheritanceService.count(
            new LambdaQueryWrapper<SysRoleInheritanceEntity>()
                .eq(SysRoleInheritanceEntity::getAncestorId, parentId)
                .eq(SysRoleInheritanceEntity::getDescendantId, childId)
        );
        
        if (count == 0) {
            SysRoleInheritanceEntity inheritance = new SysRoleInheritanceEntity();
            inheritance.setAncestorId(parentId);
            inheritance.setDescendantId(childId);
            inheritance.setDepth(1);
            inheritanceService.save(inheritance);
            log.info("添加角色继承: child={}, parent={}", childId, parentId);
        }
        
        return Result.success();
    }

    /**
     * 删除单个继承关系
     */
    @PreAuthorize("hasAuthority('role:setInheritance')")
    @DeleteMapping("/{childId}/parent/{parentId}")
    public Result<Void> removeInheritance(@PathVariable Long childId, @PathVariable Long parentId) {
        inheritanceService.remove(
            new LambdaQueryWrapper<SysRoleInheritanceEntity>()
                .eq(SysRoleInheritanceEntity::getAncestorId, parentId)
                .eq(SysRoleInheritanceEntity::getDescendantId, childId)
        );
        log.info("删除角色继承: child={}, parent={}", childId, parentId);
        return Result.success();
    }
}
