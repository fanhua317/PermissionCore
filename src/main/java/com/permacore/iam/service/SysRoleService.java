package com.permacore.iam.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.permacore.iam.domain.entity.SysPermissionEntity;
import com.permacore.iam.domain.entity.SysRoleEntity;

import java.util.List;

/**
 * <p>
 * 角色表 服务类
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
public interface SysRoleService extends IService<SysRoleEntity> {

    boolean saveRole(SysRoleEntity role);

    boolean updateRole(SysRoleEntity role);

    List<SysRoleEntity> listRoles();

    Page<SysRoleEntity> pageRoles(Integer pageNo, Integer pageSize, String roleName);

    void deleteRoleCascade(Long id);

    void assignPermissions(Long roleId, List<Long> permissionIds);

    List<Long> getRolePermissionIds(Long roleId);
    
    /**
     * 获取角色拥有的权限列表（返回完整权限对象）
     */
    List<SysPermissionEntity> getRolePermissions(Long roleId);

    void setRoleInheritance(Long roleId, Long parentId);

    List<Long> getDescendantRoleIds(Long roleId);
}
