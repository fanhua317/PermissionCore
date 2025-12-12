package com.permacore.iam.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.permacore.iam.domain.entity.RoleEntity;

import java.util.List;

public interface RoleService {
    Page<RoleEntity> page(Integer pageNo, Integer pageSize, String roleName);
    List<RoleEntity> listRoles();
    void deleteRoleCascade(Long id);
    void assignPermissions(Long roleId, java.util.List<Long> permissionIds);
    java.util.List<Long> getRolePermissionIds(Long roleId);
    void setRoleInheritance(Long roleId, Long parentId);
    java.util.List<Long> getDescendantRoleIds(Long roleId);
}
