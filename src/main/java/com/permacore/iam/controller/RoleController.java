package com.permacore.iam.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.permacore.iam.domain.entity.RoleEntity;
import com.permacore.iam.domain.vo.PageVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.service.SysRoleService;
import com.permacore.iam.service.impl.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 角色管理控制器
 */
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
    @PreAuthorize("hasAuthority('system:role:query')")
    @GetMapping("/page")
    public Result<PageVO<RoleEntity>> page(@RequestParam(defaultValue = "1") Integer pageNo,
                                           @RequestParam(defaultValue = "10") Integer pageSize,
                                           @RequestParam(required = false) String roleName) {
        Page<RoleEntity> page = roleService.pageRoles(pageNo, pageSize, roleName);
        return Result.success(PageVO.of(page));
    }

    /**
     * 获取所有角色列表（用于下拉框）
     */
    @PreAuthorize("hasAuthority('system:role:query')")
    @GetMapping("/list")
    public Result<List<RoleEntity>> list() {
        return Result.success(roleService.listRoles());
    }

    /**
     * 创建角色
     */
    @PreAuthorize("hasAuthority('role:add')")
    @PostMapping
    public Result<Void> create(@RequestBody RoleEntity role) {
        roleService.saveRole(role);
        log.info("创建角色: {}", role.getRoleName());
        return Result.success();
    }

    /**
     * 更新角色
     */
    @PreAuthorize("hasAuthority('role:edit')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody RoleEntity role) {
        role.setId(id);
        roleService.updateRole(role);
        log.info("更新角色: roleId={}", id);
        return Result.success();
    }

    /**
     * 删除角色（同时删除关联关系）
     */
    @PreAuthorize("hasAuthority('role:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.deleteRoleCascade(id);
        log.info("删除角色: roleId={}", id);
        return Result.success();
    }

    /**
     * 为角色分配权限
     */
    @PreAuthorize("hasAuthority('role:assignPermission')")
    @PostMapping("/{roleId}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long roleId, @RequestBody List<Long> permissionIds) {
        roleService.assignPermissions(roleId, permissionIds);
        log.info("分配权限: roleId={}, permissions={}", roleId, permissionIds);
        return Result.success();
    }

    /**
     * 获取角色拥有的权限
     */
    @PreAuthorize("hasAuthority('system:role:query')")
    @GetMapping("/{roleId}/permissions")
    public Result<List<Long>> getRolePermissions(@PathVariable Long roleId) {
        return Result.success(roleService.getRolePermissionIds(roleId));
    }

    /**
     * 设置角色继承关系
     * 示例：roleId=子角色, parentId=父角色
     */
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
    @PreAuthorize("hasAuthority('system:role:query')")
    @GetMapping("/{roleId}/descendants")
    public Result<List<Long>> getDescendants(@PathVariable Long roleId) {
        return Result.success(roleService.getDescendantRoleIds(roleId));
    }
}