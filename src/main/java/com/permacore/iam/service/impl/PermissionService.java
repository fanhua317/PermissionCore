package com.permacore.iam.service.impl;

import com.permacore.iam.mapper.SysPermissionMapper;
import com.permacore.iam.mapper.SysRoleInheritanceMapper;
import com.permacore.iam.mapper.SysRolePermissionMapper;
import com.permacore.iam.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限服务
 * 负责查询用户拥有的所有权限
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRoleInheritanceMapper roleInheritanceMapper;

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
        List<Long> roleIdList = userRoleMapper.selectRoleIdsByUserId(userId);
        Set<Long> directRoleIds = roleIdList != null ? new HashSet<>(roleIdList) : new HashSet<>();

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
        return roleInheritanceMapper.selectAncestorIdsByDescendantId(roleId);
    }
}

/*
 * 非 Lombok 版本示例：
 * public class PermissionService {
 *     private static final Logger log = LoggerFactory.getLogger(PermissionService.class);
 *     private final SysUserRoleMapper userRoleMapper;
 *     private final SysRolePermissionMapper rolePermissionMapper;
 *     private final SysPermissionMapper permissionMapper;
 *     private final SysRoleInheritanceMapper roleInheritanceMapper;
 *
 *     public PermissionService(SysUserRoleMapper userRoleMapper,
 *                              SysRolePermissionMapper rolePermissionMapper,
 *                              SysPermissionMapper permissionMapper,
 *                              SysRoleInheritanceMapper roleInheritanceMapper) {
 *         this.userRoleMapper = userRoleMapper;
 *         this.rolePermissionMapper = rolePermissionMapper;
 *         this.permissionMapper = permissionMapper;
 *         this.roleInheritanceMapper = roleInheritanceMapper;
 *     }
 *     // 其余方法保持不变
 * }
 */
