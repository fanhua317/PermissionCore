package com.permacore.iam.service.impl;

import com.permacore.iam.mapper.SysPermissionMapper;
import com.permacore.iam.mapper.SysRoleInheritanceMapper;
import com.permacore.iam.mapper.SysRolePermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final String ADMIN_PERMISSION = "admin:*";

    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRoleInheritanceMapper roleInheritanceMapper;

    public Set<String> getPermissionsByRoleIds(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new TreeSet<>();
        }

        Set<Long> permissionIds = rolePermissionMapper.selectPermissionIdsByRoleIds(roleIds);
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new TreeSet<>();
        }

        Set<String> permissions = permissionMapper.selectPermKeysByIds(permissionIds);
        return expandAdminPermission(permissions);
    }

    public Set<Long> getPotentialRoleIdsWithInheritance(Collection<Long> directRoleIds) {
        Set<Long> roleIds = directRoleIds != null ? new HashSet<>(directRoleIds) : new HashSet<>();
        if (roleIds.isEmpty()) {
            return roleIds;
        }

        Set<Long> inheritedRoleIds = roleInheritanceMapper.selectAllAncestorIdsByDescendantIds(roleIds);
        if (inheritedRoleIds != null && !inheritedRoleIds.isEmpty()) {
            roleIds.addAll(inheritedRoleIds);
        }
        return roleIds;
    }

    protected Set<String> expandAdminPermission(Set<String> permissions) {
        TreeSet<String> result = permissions == null ? new TreeSet<>() : new TreeSet<>(permissions);
        if (!result.contains(ADMIN_PERMISSION)) {
            return result;
        }

        Set<String> allEnabledPermissions = permissionMapper.selectAllEnabledPermKeys();
        if (allEnabledPermissions != null && !allEnabledPermissions.isEmpty()) {
            result.addAll(allEnabledPermissions);
        }
        return result;
    }
}
