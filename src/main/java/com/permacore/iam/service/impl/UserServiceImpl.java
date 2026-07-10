package com.permacore.iam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.domain.entity.SysRoleEntity;
import com.permacore.iam.domain.entity.SysSodConstraintEntity;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.entity.SysUserRoleEntity;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.mapper.SysUserRoleMapper;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.service.AuthorizationStateService;
import com.permacore.iam.service.SysSodConstraintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户详情服务（Spring Security核心接口）
 * 负责从数据库加载用户信息和权限，并提供基本的用户 CRUD/分页等方法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUserEntity>
        implements com.permacore.iam.service.UserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PermissionService permissionService;
    private final SysSodConstraintService sodConstraintService;
    private final ObjectMapper objectMapper;
    private final AuthorizationStateService authorizationStateService;

    public boolean usernameExists(String username) {
        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserEntity::getUsername, username)
                .eq(SysUserEntity::getDelFlag, 0);
        return userMapper.selectCount(wrapper) > 0;
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        if (roleIds != null && roleIds.size() > 500) {
            throw new BusinessException("单次最多分配500个角色");
        }
        if (roleIds != null && roleIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BusinessException("角色ID不能为空");
        }
        // Lock order is always role graph -> target user. A shared graph lock keeps
        // role validation consistent while allowing assignments for other users.
        roleMapper.lockAllRoleIdsShared();
        SysUserEntity targetUser = userMapper.selectByIdForUpdate(userId);
        if (targetUser == null) {
            throw new BusinessException("用户不存在: " + userId);
        }

        List<Long> normalizedRoleIds = roleIds == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(roleIds));
        validateAssignableRoles(normalizedRoleIds);

        // RBAC3: 校验静态互斥约束 (SSD - Static Separation of Duty)
        if (!normalizedRoleIds.isEmpty()) {
            checkSsdConstraints(normalizedRoleIds);
        }

        // 先删除原有角色关联
        userRoleMapper.deleteByUserId(userId);
        // 插入新的角色关联
        if (!normalizedRoleIds.isEmpty()) {
            for (Long roleId : normalizedRoleIds) {
                SysUserRoleEntity userRole = new SysUserRoleEntity();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userRoleMapper.insert(userRole);
            }
        }
        authorizationStateService.invalidateUsers(List.of(userId));
        log.info("角色分配完成: userId={}, roleIds={}", userId, normalizedRoleIds);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        if (userId == null) {
            throw new BusinessException("用户不存在: null");
        }
        deleteUsersInLockOrder(List.of(userId));
    }

    @Override
    @Transactional
    public void deleteUsers(List<Long> userIds) {
        List<Long> normalizedIds = userIds == null ? List.of()
                : userIds.stream().filter(java.util.Objects::nonNull).distinct().sorted().toList();
        if (normalizedIds.isEmpty()) {
            throw new BusinessException("请选择要删除的用户");
        }
        deleteUsersInLockOrder(normalizedIds);
    }

    /**
     * Acquire every lock before mutating relationships. All callers use the same
     * graph -> ascending user order, so overlapping batches cannot lock users in
     * opposite orders.
     */
    private void deleteUsersInLockOrder(List<Long> userIds) {
        roleMapper.lockAllRoleIdsShared();
        for (Long userId : userIds) {
            if (userMapper.selectByIdForUpdate(userId) == null) {
                throw new BusinessException("用户不存在: " + userId);
            }
        }
        for (Long userId : userIds) {
            userRoleMapper.deleteByUserId(userId);
            if (!super.removeById(userId)) {
                throw new BusinessException("删除用户失败: " + userId);
            }
        }
        authorizationStateService.invalidateUsers(userIds);
    }

    private void validateAssignableRoles(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        List<SysRoleEntity> roles = roleMapper.selectBatchIds(roleIds);
        Set<Long> validRoleIds = roles == null ? Set.of() : roles.stream()
                .filter(role -> Byte.valueOf((byte) 1).equals(role.getStatus()))
                .filter(role -> !Byte.valueOf((byte) 1).equals(role.getDelFlag()))
                .map(SysRoleEntity::getId)
                .collect(Collectors.toSet());
        List<Long> invalidRoleIds = roleIds.stream()
                .filter(roleId -> !validRoleIds.contains(roleId))
                .toList();
        if (!invalidRoleIds.isEmpty()) {
            throw new BusinessException("角色不存在或未启用: " + invalidRoleIds);
        }
    }

    /**
     * 校验静态职责分离约束 (SSD)
     * 如果要分配的角色集合违反了任何SSD约束，则抛出异常
     */
    private void checkSsdConstraints(List<Long> roleIds) {
        // 获取所有静态互斥约束 (constraint_type = 1)
        List<SysSodConstraintEntity> ssdConstraints = sodConstraintService.list(
                new LambdaQueryWrapper<SysSodConstraintEntity>()
                        .eq(SysSodConstraintEntity::getConstraintType, (byte) 1));

        Set<Long> roleIdSet = permissionService.getPotentialRoleIdsWithInheritance(roleIds);

        for (SysSodConstraintEntity constraint : ssdConstraints) {
            try {
                // 解析互斥角色ID集合 (JSON数组格式)
                List<Long> mutexRoleIds = objectMapper.readValue(
                        constraint.getRoleSet(),
                        new TypeReference<List<Long>>() {
                        });

                // 计算交集数量
                long conflictCount = mutexRoleIds.stream()
                        .distinct()
                        .filter(roleIdSet::contains)
                        .count();

                // 如果用户要分配的角色中有2个或更多互斥角色，则违反约束
                if (conflictCount >= 2) {
                    log.warn("SSD约束冲突: constraint={}, conflictRoles={}",
                            constraint.getConstraintName(),
                            mutexRoleIds.stream().filter(roleIdSet::contains).collect(Collectors.toList()));
                    throw new BusinessException("角色分配违反职责分离约束: " + constraint.getConstraintName());
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                log.error("解析SoD约束失败: constraintId={}, error={}", constraint.getId(), e.getMessage());
                throw new BusinessException("SoD约束配置无效: " + constraint.getId());
            }
        }
    }

    @Override
    public List<Long> getUserRoleIds(Long userId) {
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        return roleIds != null ? roleIds : Collections.emptyList();
    }

    @Override
    public List<SysRoleEntity> getUserRoles(Long userId) {
        List<Long> roleIds = getUserRoleIds(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return roleMapper.selectBatchIds(roleIds);
    }

    @Override
    public Page<SysUserEntity> page(Page<SysUserEntity> page, LambdaQueryWrapper<SysUserEntity> wrapper) {
        return super.page(page, wrapper);
    }
}
