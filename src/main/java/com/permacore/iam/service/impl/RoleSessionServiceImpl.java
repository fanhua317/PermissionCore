package com.permacore.iam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.domain.entity.SysPermissionEntity;
import com.permacore.iam.domain.entity.SysRoleEntity;
import com.permacore.iam.domain.entity.SysSodConstraintEntity;
import com.permacore.iam.domain.vo.DsdConflictVO;
import com.permacore.iam.domain.vo.SessionRoleStateVO;
import com.permacore.iam.domain.vo.SessionRoleVO;
import com.permacore.iam.mapper.SysPermissionMapper;
import com.permacore.iam.mapper.SysRoleInheritanceMapper;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.mapper.SysRolePermissionMapper;
import com.permacore.iam.mapper.SysUserRoleMapper;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.service.RoleSessionService;
import com.permacore.iam.service.SysSodConstraintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleSessionServiceImpl implements RoleSessionService {

    private static final String ADMIN_PERMISSION = "admin:*";

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRoleInheritanceMapper roleInheritanceMapper;
    private final SysSodConstraintService sodConstraintService;
    private final ObjectMapper objectMapper;

    @Override
    public SessionRoleStateVO buildDefaultState(Long userId) {
        List<SysRoleEntity> assignableRoles = getAssignableRoles(userId);
        LinkedHashSet<Long> selectedRoleIds = new LinkedHashSet<>();
        List<DsdConflictVO> lastConflicts = new ArrayList<>();

        for (SysRoleEntity role : assignableRoles) {
            LinkedHashSet<Long> candidate = new LinkedHashSet<>(selectedRoleIds);
            candidate.add(role.getId());
            Set<Long> effectiveRoleIds = resolveEffectiveRoleIds(candidate);
            List<DsdConflictVO> conflicts = findDsdConflicts(effectiveRoleIds);
            if (conflicts.isEmpty()) {
                selectedRoleIds.add(role.getId());
            } else {
                lastConflicts = conflicts;
            }
        }

        SessionRoleStateVO state = buildValidState(userId, assignableRoles, selectedRoleIds);
        state.setDsdConflicts(lastConflicts);
        return state;
    }

    @Override
    public SessionRoleStateVO buildState(Long userId, Collection<Long> activeRoleIds) {
        List<SysRoleEntity> assignableRoles = getAssignableRoles(userId);
        LinkedHashSet<Long> normalizedActiveRoleIds = normalizeRoleIds(activeRoleIds);
        Set<Long> assignableRoleIds = assignableRoles.stream()
                .map(SysRoleEntity::getId)
                .collect(Collectors.toSet());

        List<Long> illegalRoleIds = normalizedActiveRoleIds.stream()
                .filter(roleId -> !assignableRoleIds.contains(roleId))
                .collect(Collectors.toList());
        if (!illegalRoleIds.isEmpty()) {
            throw new BusinessException("只能激活当前用户已分配且启用的角色: " + illegalRoleIds);
        }

        return buildValidState(userId, assignableRoles, normalizedActiveRoleIds);
    }

    @Override
    public Set<Long> resolveEffectiveRoleIds(Collection<Long> directRoleIds) {
        LinkedHashSet<Long> effectiveRoleIds = normalizeRoleIds(directRoleIds);
        if (effectiveRoleIds.isEmpty()) {
            return effectiveRoleIds;
        }

        Set<Long> inheritedRoleIds = roleInheritanceMapper.selectAncestorIdsByDescendantIds(effectiveRoleIds);
        if (inheritedRoleIds != null && !inheritedRoleIds.isEmpty()) {
            effectiveRoleIds.addAll(inheritedRoleIds);
        }
        return effectiveRoleIds;
    }

    @Override
    public Set<String> getPermissionsByEffectiveRoleIds(Set<Long> effectiveRoleIds) {
        if (CollectionUtils.isEmpty(effectiveRoleIds)) {
            return new TreeSet<>();
        }
        Set<Long> permissionIds = rolePermissionMapper.selectPermissionIdsByRoleIds(effectiveRoleIds);
        if (CollectionUtils.isEmpty(permissionIds)) {
            return new TreeSet<>();
        }
        Set<String> permissions = permissionMapper.selectPermKeysByIds(permissionIds);
        return expandAdminPermission(permissions);
    }

    @Override
    public List<Long> parseRoleIdsClaim(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?>) {
            return ((Collection<?>) value).stream()
                    .map(this::toLong)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        if (value instanceof String) {
            String text = value.toString().trim();
            if (text.isEmpty()) {
                return List.of();
            }
            return List.of(text.split(",")).stream()
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .map(this::toLong)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        Long singleValue = toLong(value);
        return singleValue == null ? List.of() : List.of(singleValue);
    }

    @Override
    public Map<String, Object> buildJwtClaims(Long userId, String username, String nickname, SessionRoleStateVO state) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("nickname", nickname);
        claims.put("activeRoleIds", state.getActiveRoleIds());
        claims.put("effectiveRoleIds", state.getEffectiveRoleIds());
        claims.put("permissions", state.getPermissions());
        return claims;
    }

    @Override
    public void appendSessionState(Map<String, Object> response, SessionRoleStateVO state) {
        response.put("activeRoleIds", state.getActiveRoleIds());
        response.put("effectiveRoleIds", state.getEffectiveRoleIds());
        response.put("permissions", state.getPermissions());
        response.put("roles", state.getRoles());
        response.put("dsdConflicts", state.getDsdConflicts());
    }

    private SessionRoleStateVO buildValidState(Long userId, List<SysRoleEntity> assignableRoles,
            Collection<Long> activeRoleIds) {
        LinkedHashSet<Long> activeIdSet = normalizeRoleIds(activeRoleIds);
        Set<Long> effectiveRoleIds = resolveEffectiveRoleIds(activeIdSet);
        List<DsdConflictVO> conflicts = findDsdConflicts(effectiveRoleIds);
        if (!conflicts.isEmpty()) {
            String names = conflicts.stream()
                    .map(DsdConflictVO::getConstraintName)
                    .collect(Collectors.joining(", "));
            throw new BusinessException("会话角色激活违反动态职责分离约束: " + names);
        }

        Set<String> permissions = getPermissionsByEffectiveRoleIds(effectiveRoleIds);
        SessionRoleStateVO state = new SessionRoleStateVO();
        state.setActiveRoleIds(new ArrayList<>(activeIdSet));
        state.setEffectiveRoleIds(effectiveRoleIds.stream().sorted().collect(Collectors.toList()));
        state.setPermissions(new ArrayList<>(permissions));
        state.setRoles(buildRoleVOs(assignableRoles, activeIdSet, effectiveRoleIds));
        state.setDsdConflicts(conflicts);
        log.debug("Build role session state: userId={}, active={}, effective={}, permissionCount={}",
                userId, activeIdSet, effectiveRoleIds, permissions.size());
        return state;
    }

    private List<SysRoleEntity> getAssignableRoles(Long userId) {
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }

        List<SysRoleEntity> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRoleEntity>()
                .in(SysRoleEntity::getId, roleIds)
                .eq(SysRoleEntity::getStatus, (byte) 1)
                .eq(SysRoleEntity::getDelFlag, (byte) 0));
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }

        return roles.stream()
                .sorted(Comparator
                        .comparing((SysRoleEntity role) -> role.getSortOrder() == null
                                ? Integer.MAX_VALUE
                                : role.getSortOrder())
                        .thenComparing(SysRoleEntity::getId))
                .collect(Collectors.toList());
    }

    private List<SessionRoleVO> buildRoleVOs(List<SysRoleEntity> roles, Set<Long> activeRoleIds,
            Set<Long> effectiveRoleIds) {
        return roles.stream().map(role -> {
            SessionRoleVO vo = new SessionRoleVO();
            vo.setId(role.getId());
            vo.setRoleKey(role.getRoleKey());
            vo.setRoleName(role.getRoleName());
            vo.setSortOrder(role.getSortOrder());
            vo.setActive(activeRoleIds.contains(role.getId()));
            vo.setEffective(effectiveRoleIds.contains(role.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

    private List<DsdConflictVO> findDsdConflicts(Set<Long> effectiveRoleIds) {
        if (CollectionUtils.isEmpty(effectiveRoleIds)) {
            return List.of();
        }

        List<SysSodConstraintEntity> constraints = sodConstraintService.list(
                new LambdaQueryWrapper<SysSodConstraintEntity>()
                        .eq(SysSodConstraintEntity::getConstraintType, (byte) 2));
        if (constraints == null || constraints.isEmpty()) {
            return List.of();
        }

        Map<Long, SysRoleEntity> roleMap = loadRoleMap(effectiveRoleIds);
        List<DsdConflictVO> conflicts = new ArrayList<>();
        for (SysSodConstraintEntity constraint : constraints) {
            List<Long> mutexRoleIds = parseRoleSet(constraint);
            List<Long> matchedRoleIds = mutexRoleIds.stream()
                    .filter(effectiveRoleIds::contains)
                    .distinct()
                    .collect(Collectors.toList());
            if (matchedRoleIds.size() >= 2) {
                DsdConflictVO conflict = new DsdConflictVO();
                conflict.setConstraintId(constraint.getId());
                conflict.setConstraintName(constraint.getConstraintName());
                conflict.setRoleIds(matchedRoleIds);
                conflict.setRoleNames(matchedRoleIds.stream()
                        .map(roleMap::get)
                        .filter(Objects::nonNull)
                        .map(SysRoleEntity::getRoleName)
                        .collect(Collectors.toList()));
                conflicts.add(conflict);
            }
        }
        return conflicts;
    }

    private Map<Long, SysRoleEntity> loadRoleMap(Collection<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return Map.of();
        }
        List<SysRoleEntity> roles = roleMapper.selectBatchIds(roleIds);
        if (roles == null || roles.isEmpty()) {
            return Map.of();
        }
        return roles.stream().collect(Collectors.toMap(SysRoleEntity::getId, role -> role, (a, b) -> a));
    }

    private List<Long> parseRoleSet(SysSodConstraintEntity constraint) {
        try {
            return objectMapper.readValue(constraint.getRoleSet(), new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            log.warn("Ignore invalid SoD role set: constraintId={}, error={}", constraint.getId(), e.getMessage());
            return List.of();
        }
    }

    private LinkedHashSet<Long> normalizeRoleIds(Collection<Long> roleIds) {
        if (roleIds == null) {
            return new LinkedHashSet<>();
        }
        return roleIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Set<String> expandAdminPermission(Set<String> permissions) {
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
