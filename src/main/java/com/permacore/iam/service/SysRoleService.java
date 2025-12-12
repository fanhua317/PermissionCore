package com.permacore.iam.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.permacore.iam.domain.entity.RoleEntity;
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

    boolean saveRole(RoleEntity role);

    boolean updateRole(RoleEntity role);

    List<RoleEntity> listRoles();

    Page<RoleEntity> pageRoles(Integer pageNo, Integer pageSize, String roleName);

    void deleteRoleCascade(Long id);

    void assignPermissions(Long roleId, List<Long> permissionIds);

    List<Long> getRolePermissionIds(Long roleId);

    void setRoleInheritance(Long roleId, Long parentId);

    List<Long> getDescendantRoleIds(Long roleId);
}
