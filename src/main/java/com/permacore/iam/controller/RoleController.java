package com.permacore.iam.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.permacore.iam.annotation.OperLog;
import com.permacore.iam.domain.entity.SysRoleEntity;
import com.permacore.iam.domain.vo.PageVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.service.SysRoleService;
import com.permacore.iam.service.impl.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 角色管理控制器
 */
@Tag(name = "角色管理", description = "角色增删改查及权限分配")
@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
public class RoleController {

    private static final Logger log = LoggerFactory.getLogger(RoleController.class);

    private final SysRoleService roleService;
    private final PermissionService permissionService;

    /**
     * 分页查询角色
     */
    @Operation(summary = "分页查询角色", description = "根据条件分页查询角色列表")
    @PreAuthorize("hasAuthority('system:role:query')")
    @GetMapping("/page")
    public Result<PageVO<SysRoleEntity>> page(@RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                           @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                           @RequestParam(name = "roleName", required = false) String roleName) {
        Page<SysRoleEntity> page = roleService.pageRoles(pageNo, pageSize, roleName);
        return Result.success(PageVO.of(page));
    }

    /**
     * 获取所有角色列表（用于下拉框）
     */
    @Operation(summary = "获取所有角色", description = "获取所有角色列表（不分页）")
    @PreAuthorize("hasAuthority('system:role:query')")
    @GetMapping("/list")
    public Result<List<SysRoleEntity>> list() {
        return Result.success(roleService.listRoles());
    }

    /**
     * 创建角色
     */
    @Operation(summary = "创建角色", description = "新增角色")
    @OperLog(title = "创建角色", businessType = 1)
    @PreAuthorize("hasAuthority('role:add')")
    @PostMapping
    public Result<Void> create(@RequestBody SysRoleEntity role) {
        roleService.saveRole(role);
        log.info("创建角色: {}", role.getRoleName());
        return Result.success();
    }

    /**
     * 更新角色
     */
    @Operation(summary = "更新角色", description = "更新角色信息")
    @OperLog(title = "更新角色", businessType = 2)
    @PreAuthorize("hasAuthority('role:edit')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysRoleEntity role) {
        role.setId(id);
        roleService.updateRole(role);
        log.info("更新角色: roleId={}", id);
        return Result.success();
    }

    /**
     * 删除角色（同时删除关联关系）
     */
    @Operation(summary = "删除角色", description = "删除角色及其关联关系")
    @OperLog(title = "删除角色", businessType = 3)
    @PreAuthorize("hasAuthority('role:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.deleteRoleCascade(id);
        log.info("删除角色: roleId={}", id);
        return Result.success();
    }

    /**
     * 为角色分配权限 (POST)
     */
    @Operation(summary = "分配权限(POST)", description = "为角色分配权限")
    @OperLog(title = "分配权限", businessType = 2)
    @PreAuthorize("hasAuthority('role:assignPermission')")
    @PostMapping("/{roleId}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long roleId, @RequestBody List<Long> permissionIds) {
        roleService.assignPermissions(roleId, permissionIds);
        log.info("分配权限: roleId={}, permissions={}", roleId, permissionIds);
        return Result.success();
    }

    /**
     * 为角色分配权限 (PUT - 前端使用此接口)
     */
    @Operation(summary = "分配权限(PUT)", description = "为角色分配权限")
    @PreAuthorize("hasAuthority('role:assignPermission')")
    @PutMapping("/{roleId}/permissions")
    public Result<Void> updatePermissions(@PathVariable Long roleId, 
            @RequestBody com.permacore.iam.domain.vo.AssignPermissionsVO vo) {
        roleService.assignPermissions(roleId, vo.getPermissionIds());
        log.info("更新权限: roleId={}, permissions={}", roleId, vo.getPermissionIds());
        return Result.success();
    }

    /**
     * 获取角色拥有的权限（返回权限对象列表）
     */
    @Operation(summary = "获取角色权限", description = "获取角色拥有的权限列表")
    @GetMapping("/{roleId}/permissions")
    public Result<List<com.permacore.iam.domain.entity.SysPermissionEntity>> getRolePermissions(@PathVariable Long roleId) {
        return Result.success(roleService.getRolePermissions(roleId));
    }

    /**
     * 设置角色继承关系
     * 示例：roleId=子角色, parentId=父角色
     */
    @Operation(summary = "设置角色继承", description = "设置角色继承关系")
    @PreAuthorize("hasAuthority('role:setInheritance')")
    @PostMapping("/{roleId}/inherit/{parentId}")
    public Result<Void> setInheritance(@PathVariable Long roleId, @PathVariable Long parentId) {
        roleService.setRoleInheritance(roleId, parentId);
        log.info("设置角色继承: roleId={}, parentId={}", roleId, parentId);
        return Result.success();
    }

    /**
     * 获取角色的角色树（包含所有后代）
     */
    @Operation(summary = "获取角色后代", description = "获取角色的所有后代角色ID")
    @PreAuthorize("hasAuthority('system:role:query')")
    @GetMapping("/{roleId}/descendants")
    public Result<List<Long>> getDescendants(@PathVariable Long roleId) {
        return Result.success(roleService.getDescendantRoleIds(roleId));
    }
}