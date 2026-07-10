package com.permacore.iam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.domain.entity.SysRoleEntity;
import com.permacore.iam.domain.entity.SysSodConstraintEntity;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.vo.SodConstraintVO;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.mapper.SysSodConstraintMapper;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.mapper.SysUserRoleMapper;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.service.AuthorizationStateService;
import com.permacore.iam.service.SysSodConstraintService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SysSodConstraintServiceImpl extends ServiceImpl<SysSodConstraintMapper, SysSodConstraintEntity>
        implements SysSodConstraintService {

    private final ObjectMapper objectMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PermissionService permissionService;
    private final AuthorizationStateService authorizationStateService;

    @Override
    @Transactional
    public void createConstraint(SodConstraintVO vo) {
        roleMapper.lockAllRoleIds();
        List<Long> roleIds = validateAndNormalizeRoleSet(vo);
        validateExistingAssignments(vo.getConstraintType(), roleIds);

        SysSodConstraintEntity entity = new SysSodConstraintEntity();
        apply(entity, vo, roleIds);
        entity.setCreateTime(LocalDateTime.now());
        save(entity);
        authorizationStateService.invalidateAllUsers();
    }

    @Override
    @Transactional
    public void updateConstraint(Long id, SodConstraintVO vo) {
        roleMapper.lockAllRoleIds();
        SysSodConstraintEntity entity = getById(id);
        if (entity == null) {
            throw new BusinessException("SoD约束不存在: " + id);
        }
        List<Long> roleIds = validateAndNormalizeRoleSet(vo);
        validateExistingAssignments(vo.getConstraintType(), roleIds);
        apply(entity, vo, roleIds);
        updateById(entity);
        authorizationStateService.invalidateAllUsers();
    }

    @Override
    @Transactional
    public void deleteConstraint(Long id) {
        roleMapper.lockAllRoleIds();
        if (!removeById(id)) {
            throw new BusinessException("SoD约束不存在: " + id);
        }
        authorizationStateService.invalidateAllUsers();
    }

    private List<Long> validateAndNormalizeRoleSet(SodConstraintVO vo) {
        if (vo == null || vo.getConstraintType() == null) {
            throw new BusinessException("约束类型不能为空");
        }
        List<Long> parsed;
        try {
            parsed = objectMapper.readValue(vo.getRoleSet(), new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            throw new BusinessException("互斥角色必须是合法的角色ID数组");
        }
        if (parsed == null) {
            throw new BusinessException("互斥角色必须是合法的角色ID数组");
        }
        List<Long> roleIds = new LinkedHashSet<>(parsed.stream()
                .filter(Objects::nonNull)
                .toList()).stream().toList();
        if (roleIds.size() < 2) {
            throw new BusinessException("SoD约束至少需要两个不同角色");
        }

        List<SysRoleEntity> roles = roleMapper.selectBatchIds(roleIds);
        Set<Long> existingIds = roles == null ? Set.of() : roles.stream()
                .filter(role -> !Byte.valueOf((byte) 1).equals(role.getDelFlag()))
                .map(SysRoleEntity::getId)
                .collect(Collectors.toSet());
        List<Long> missingIds = roleIds.stream().filter(id -> !existingIds.contains(id)).toList();
        if (!missingIds.isEmpty()) {
            throw new BusinessException("SoD约束引用了不存在的角色: " + missingIds);
        }
        return roleIds;
    }

    private void validateExistingAssignments(Byte constraintType, List<Long> mutexRoleIds) {
        if (!Byte.valueOf((byte) 1).equals(constraintType)) {
            return;
        }
        List<SysUserEntity> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUserEntity>()
                        .select(SysUserEntity::getId)
                        .eq(SysUserEntity::getDelFlag, (byte) 0));
        for (SysUserEntity user : users) {
            List<Long> directRoleIds = userRoleMapper.selectRoleIdsByUserId(user.getId());
            Set<Long> effectiveRoleIds = permissionService.getPotentialRoleIdsWithInheritance(directRoleIds);
            long conflictCount = mutexRoleIds.stream().distinct().filter(effectiveRoleIds::contains).count();
            if (conflictCount >= 2) {
                throw new BusinessException("该SSD约束会使现有用户违反职责分离: userId=" + user.getId());
            }
        }
    }

    private void apply(SysSodConstraintEntity entity, SodConstraintVO vo, List<Long> roleIds) {
        entity.setConstraintName(vo.getConstraintName().trim());
        entity.setConstraintType(vo.getConstraintType());
        try {
            entity.setRoleSet(objectMapper.writeValueAsString(roleIds));
        } catch (Exception e) {
            throw new BusinessException("SoD约束序列化失败");
        }
    }
}
