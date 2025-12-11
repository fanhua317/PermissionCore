package com.permacore.iam.service.impl;

import com.permacore.iam.mapper.PermissionMapper;
import com.permacore.iam.mapper.RolePermissionMapper;
import com.permacore.iam.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * 权限服务
 * 负责查询用户拥有的所有权限
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;

    /**
     * 获取用户的所有权限（包含角色继承的权限）
     */
    public Set<String> getUserPermissions(Long userId) {
        // 1. 查询用户的所有角色ID（包含继承的角色）
        Set<Long> roleIds = getUserRoleIdsWithInheritance(userId);

        if (roleIds.isEmpty()) {
            return new HashSet<>();
        }

        // 2. 查询这些角色的所有权限
        Set<Long> permissionIds = rolePermissionMapper.selectPermissionIdsByRoleIds(roleIds);

        if (permissionIds.isEmpty()) {
            return new HashSet<>();
        }

        // 3. 查询权限标识
        return permissionMapper.selectPermKeysByIds(permissionIds);
    }

    /**
     * 获取用户的所有角色ID（包含继承的）
     * 性能优化：可在Redis中缓存角色树
     */
    private Set<Long> getUserRoleIdsWithInheritance(Long userId) {
        // 1. 查询直接分配的角色
        Set<Long> directRoleIds = new HashSet<>(userRoleMapper.selectRoleIdsByUserId(userId));

        // 2. 查询每个角色的所有祖先角色（继承）
        Set<Long> inheritedRoleIds = new HashSet<>();
        for (Long roleId : directRoleIds) {
            inheritedRoleIds.addAll(getAncestorRoleIds(roleId));
        }

        directRoleIds.addAll(inheritedRoleIds);
        return directRoleIds;
    }

    /**
     * 获取角色的所有祖先角色ID（递归）
     */
    private Set<Long> getAncestorRoleIds(Long roleId) {
        Set<Long> ancestors = new HashSet<>();

        // 这里简化实现，实际可通过sys_role_inheritance表查询
        // TODO: 实现完整的角色继承查询
        // 示例：SELECT ancestor_id FROM sys_role_inheritance WHERE descendant_id = #{roleId}

        return ancestors;
    }
}