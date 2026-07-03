package com.permacore.iam.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.domain.entity.SysRoleEntity;
import com.permacore.iam.domain.entity.SysRoleInheritanceEntity;
import com.permacore.iam.domain.entity.SysSodConstraintEntity;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.entity.SysUserRoleEntity;
import com.permacore.iam.mapper.SysRoleInheritanceMapper;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.mapper.SysUserRoleMapper;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.service.SysRoleInheritanceService;
import com.permacore.iam.service.SysSodConstraintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 角色继承关系表 服务实现类
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SysRoleInheritanceServiceImpl extends ServiceImpl<SysRoleInheritanceMapper, SysRoleInheritanceEntity> implements SysRoleInheritanceService {

    private final SysRoleInheritanceMapper roleInheritanceMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final SysSodConstraintService sodConstraintService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void updateParentRoles(Long roleId, List<Long> parentRoleIds) {
        if (roleId == null) {
            throw new BusinessException("角色ID不能为空");
        }
        validateRoleExists(roleId, "目标角色不存在");

        List<Long> normalizedParentIds = normalizeParentRoleIds(parentRoleIds);
        if (normalizedParentIds.contains(roleId)) {
            throw new BusinessException("角色不能继承自身");
        }
        validateParentRolesExist(normalizedParentIds);

        Map<Long, Set<Long>> proposedParentMap = buildProposedParentMap(roleId, normalizedParentIds);
        validateNoCycle(roleId, normalizedParentIds, proposedParentMap);
        validateExistingUsersAgainstSsd(proposedParentMap);

        roleInheritanceMapper.delete(
                new LambdaQueryWrapper<SysRoleInheritanceEntity>()
                        .eq(SysRoleInheritanceEntity::getDescendantId, roleId));

        for (Long parentId : normalizedParentIds) {
            SysRoleInheritanceEntity inheritance = new SysRoleInheritanceEntity();
            inheritance.setAncestorId(parentId);
            inheritance.setDescendantId(roleId);
            inheritance.setDepth(1);
            roleInheritanceMapper.insert(inheritance);
        }
        log.info("角色继承更新完成: roleId={}, parents={}", roleId, normalizedParentIds);
    }

    @Override
    @Transactional
    public void addInheritance(Long childId, Long parentId) {
        List<Long> parents = getDirectParentIds(childId);
        if (parentId != null && !parents.contains(parentId)) {
            parents.add(parentId);
        }
        updateParentRoles(childId, parents);
    }

    @Override
    @Transactional
    public void removeInheritance(Long childId, Long parentId) {
        List<Long> parents = getDirectParentIds(childId).stream()
                .filter(id -> !Objects.equals(id, parentId))
                .collect(Collectors.toCollection(ArrayList::new));
        updateParentRoles(childId, parents);
    }

    private List<Long> getDirectParentIds(Long roleId) {
        if (roleId == null) {
            return new ArrayList<>();
        }
        return roleInheritanceMapper.selectList(
                        new LambdaQueryWrapper<SysRoleInheritanceEntity>()
                                .eq(SysRoleInheritanceEntity::getDescendantId, roleId)
                                .eq(SysRoleInheritanceEntity::getDepth, 1))
                .stream()
                .map(SysRoleInheritanceEntity::getAncestorId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Long> normalizeParentRoleIds(List<Long> parentRoleIds) {
        if (parentRoleIds == null || parentRoleIds.isEmpty()) {
            return List.of();
        }
        return parentRoleIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private void validateRoleExists(Long roleId, String errorMessage) {
        SysRoleEntity role = roleMapper.selectById(roleId);
        if (role == null || Byte.valueOf((byte) 1).equals(role.getDelFlag())) {
            throw new BusinessException(errorMessage + ": " + roleId);
        }
    }

    private void validateParentRolesExist(List<Long> parentRoleIds) {
        if (parentRoleIds.isEmpty()) {
            return;
        }
        List<SysRoleEntity> roles = roleMapper.selectBatchIds(parentRoleIds);
        Set<Long> existingRoleIds = roles == null ? Set.of() : roles.stream()
                .filter(role -> !Byte.valueOf((byte) 1).equals(role.getDelFlag()))
                .map(SysRoleEntity::getId)
                .collect(Collectors.toSet());
        List<Long> missingRoleIds = parentRoleIds.stream()
                .filter(id -> !existingRoleIds.contains(id))
                .collect(Collectors.toList());
        if (!missingRoleIds.isEmpty()) {
            throw new BusinessException("父角色不存在: " + missingRoleIds);
        }
    }

    private Map<Long, Set<Long>> buildProposedParentMap(Long roleId, List<Long> parentRoleIds) {
        List<SysRoleInheritanceEntity> currentEdges = roleInheritanceMapper.selectList(
                new LambdaQueryWrapper<SysRoleInheritanceEntity>()
                        .eq(SysRoleInheritanceEntity::getDepth, 1));
        if (currentEdges == null) {
            currentEdges = List.of();
        }

        Map<Long, Set<Long>> parentMap = new HashMap<>();
        for (SysRoleInheritanceEntity edge : currentEdges) {
            if (Objects.equals(edge.getDescendantId(), roleId)) {
                continue;
            }
            if (edge.getDescendantId() == null || edge.getAncestorId() == null) {
                continue;
            }
            parentMap.computeIfAbsent(edge.getDescendantId(), key -> new LinkedHashSet<>())
                    .add(edge.getAncestorId());
        }
        parentMap.put(roleId, new LinkedHashSet<>(parentRoleIds));
        return parentMap;
    }

    private void validateNoCycle(Long roleId, List<Long> parentRoleIds, Map<Long, Set<Long>> parentMap) {
        for (Long parentId : parentRoleIds) {
            Set<Long> ancestors = collectAncestors(parentId, parentMap);
            if (ancestors.contains(roleId)) {
                throw new BusinessException("角色继承关系会形成环: roleId=" + roleId + ", parentId=" + parentId);
            }
        }
    }

    private Set<Long> collectAncestors(Long roleId, Map<Long, Set<Long>> parentMap) {
        if (roleId == null) {
            return Set.of();
        }
        Set<Long> ancestors = new LinkedHashSet<>();
        Deque<Long> stack = new ArrayDeque<>(parentMap.getOrDefault(roleId, Collections.emptySet()));
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            if (current == null || !ancestors.add(current)) {
                continue;
            }
            stack.addAll(parentMap.getOrDefault(current, Collections.emptySet()));
        }
        return ancestors;
    }

    private void validateExistingUsersAgainstSsd(Map<Long, Set<Long>> parentMap) {
        List<SysUserEntity> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUserEntity>()
                        .eq(SysUserEntity::getDelFlag, (byte) 0));
        if (users == null || users.isEmpty()) {
            return;
        }
        Set<Long> userIds = users.stream()
                .map(SysUserEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }

        List<SysUserRoleEntity> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<>());
        if (userRoles == null || userRoles.isEmpty()) {
            return;
        }
        Map<Long, Set<Long>> directRolesByUser = new HashMap<>();
        for (SysUserRoleEntity userRole : userRoles) {
            if (userRole.getUserId() == null || userRole.getRoleId() == null || !userIds.contains(userRole.getUserId())) {
                continue;
            }
            directRolesByUser.computeIfAbsent(userRole.getUserId(), key -> new LinkedHashSet<>())
                    .add(userRole.getRoleId());
        }
        if (directRolesByUser.isEmpty()) {
            return;
        }

        List<SysSodConstraintEntity> ssdConstraints = sodConstraintService.list(
                new LambdaQueryWrapper<SysSodConstraintEntity>()
                        .eq(SysSodConstraintEntity::getConstraintType, (byte) 1));
        if (ssdConstraints == null || ssdConstraints.isEmpty()) {
            return;
        }

        for (Map.Entry<Long, Set<Long>> entry : directRolesByUser.entrySet()) {
            Set<Long> effectiveRoleIds = expandEffectiveRoles(entry.getValue(), parentMap);
            for (SysSodConstraintEntity constraint : ssdConstraints) {
                List<Long> mutexRoleIds = parseRoleSet(constraint);
                List<Long> conflictRoleIds = mutexRoleIds.stream()
                        .filter(effectiveRoleIds::contains)
                        .distinct()
                        .collect(Collectors.toList());
                if (conflictRoleIds.size() >= 2) {
                    throw new BusinessException("修改角色继承会导致已有用户违反静态职责分离约束: 用户ID="
                            + entry.getKey()
                            + ", 约束=" + constraint.getConstraintName()
                            + ", 冲突角色ID=" + conflictRoleIds);
                }
            }
        }
    }

    private Set<Long> expandEffectiveRoles(Collection<Long> directRoleIds, Map<Long, Set<Long>> parentMap) {
        Set<Long> effectiveRoleIds = new LinkedHashSet<>();
        for (Long roleId : directRoleIds) {
            if (roleId == null) {
                continue;
            }
            effectiveRoleIds.add(roleId);
            effectiveRoleIds.addAll(collectAncestors(roleId, parentMap));
        }
        return effectiveRoleIds;
    }

    private List<Long> parseRoleSet(SysSodConstraintEntity constraint) {
        try {
            return objectMapper.readValue(
                    constraint.getRoleSet(),
                    new TypeReference<List<Long>>() {
                    });
        } catch (Exception e) {
            log.warn("解析SSD约束失败: constraintId={}, roleSet={}", constraint.getId(), constraint.getRoleSet());
            return List.of();
        }
    }
}
