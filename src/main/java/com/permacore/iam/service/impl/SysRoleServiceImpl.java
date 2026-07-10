package com.permacore.iam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.domain.entity.SysPermissionEntity;
import com.permacore.iam.domain.entity.SysRoleEntity;
import com.permacore.iam.domain.entity.SysRoleInheritanceEntity;
import com.permacore.iam.domain.entity.SysRolePermissionEntity;
import com.permacore.iam.domain.entity.SysSodConstraintEntity;
import com.permacore.iam.mapper.SysPermissionMapper;
import com.permacore.iam.mapper.SysRoleInheritanceMapper;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.mapper.SysRolePermissionMapper;
import com.permacore.iam.mapper.SysUserRoleMapper;
import com.permacore.iam.mapper.SysSodConstraintMapper;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.service.AuthorizationStateService;
import com.permacore.iam.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 角色表 服务实现类
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRoleEntity> implements SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRoleInheritanceMapper roleInheritanceMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysSodConstraintMapper sodConstraintMapper;
    private final ObjectMapper objectMapper;
    private final AuthorizationStateService authorizationStateService;

    @Override
    public boolean saveRole(SysRoleEntity role) {
        validateNewRole(role);
        role.setId(null);
        role.setDelFlag((byte) 0);
        if (role.getStatus() == null) {
            role.setStatus((byte) 1);
        }
        return super.save(role);
    }

    @Override
    @Transactional
    public boolean updateRole(SysRoleEntity role) {
        roleMapper.lockAllRoleIds();
        validateRoleUpdate(role);
        SysRoleEntity existing = super.getById(role.getId());
        if (existing == null) {
            throw new BusinessException("角色不存在: " + role.getId());
        }
        if (role.getRoleName() == null && role.getRemark() == null
                && role.getStatus() == null && role.getSortOrder() == null) {
            return true;
        }
        boolean updated = super.updateById(role);
        if (updated) {
            authorizationStateService.invalidateAllUsers();
        }
        return updated;
    }

    @Override
    public List<SysRoleEntity> listRoles() {
        return super.list();
    }

    @Override
    public Page<SysRoleEntity> pageRoles(Integer pageNo, Integer pageSize, String roleName) {
        Page<SysRoleEntity> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SysRoleEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(roleName)) {
            wrapper.like(SysRoleEntity::getRoleName, roleName);
        }
        return super.page(page, wrapper);
    }

    @Override
    @Transactional
    public void deleteRoleCascade(Long id) {
        roleMapper.lockAllRoleIds();
        SysRoleEntity role = super.getById(id);
        if (role == null) {
            throw new BusinessException("角色不存在: " + id);
        }
        if (Byte.valueOf((byte) 1).equals(role.getRoleType())) {
            throw new BusinessException("系统角色不允许删除");
        }
        rejectRoleReferencedBySod(id);
        // 删除角色权限关联
        rolePermissionMapper.deleteByRoleId(id);
        userRoleMapper.deleteByRoleId(id);
        // 删除角色继承关系（作为后代角色）
        LambdaQueryWrapper<SysRoleInheritanceEntity> descendantWrapper = new LambdaQueryWrapper<>();
        descendantWrapper.eq(SysRoleInheritanceEntity::getDescendantId, id);
        roleInheritanceMapper.delete(descendantWrapper);
        // 删除角色继承关系（作为祖先角色）
        LambdaQueryWrapper<SysRoleInheritanceEntity> ancestorWrapper = new LambdaQueryWrapper<>();
        ancestorWrapper.eq(SysRoleInheritanceEntity::getAncestorId, id);
        roleInheritanceMapper.delete(ancestorWrapper);
        // 删除角色
        super.removeById(id);
        authorizationStateService.invalidateAllUsers();
        log.info("级联删除角色完成: roleId={}", id);
    }

    @Override
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        if (permissionIds != null && permissionIds.size() > 2000) {
            throw new BusinessException("单次最多分配2000个权限");
        }
        if (permissionIds != null && permissionIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException("权限ID不能为空");
        }
        roleMapper.lockAllRoleIds();
        SysRoleEntity role = super.getById(roleId);
        if (role == null || Byte.valueOf((byte) 1).equals(role.getDelFlag())) {
            throw new BusinessException("角色不存在: " + roleId);
        }
        List<Long> normalizedPermissionIds = permissionIds == null ? List.of()
                : permissionIds.stream().distinct().toList();
        validatePermissions(normalizedPermissionIds);
        // 先删除原有权限
        rolePermissionMapper.deleteByRoleId(roleId);
        // 批量插入新权限
        if (!normalizedPermissionIds.isEmpty()) {
            List<SysRolePermissionEntity> records = normalizedPermissionIds.stream()
                    .map(permId -> {
                        SysRolePermissionEntity rp = new SysRolePermissionEntity();
                        rp.setRoleId(roleId);
                        rp.setPermissionId(permId);
                        return rp;
                    })
                    .collect(Collectors.toList());
            rolePermissionMapper.insertBatch(records);
        }
        authorizationStateService.invalidateAllUsers();
        log.info("权限分配完成: roleId={}, permissionIds={}", roleId, normalizedPermissionIds);
    }

    private void validateNewRole(SysRoleEntity role) {
        if (role == null || !StringUtils.hasText(role.getRoleKey()) || !StringUtils.hasText(role.getRoleName())) {
            throw new BusinessException("角色标识和角色名称不能为空");
        }
        role.setRoleKey(role.getRoleKey().trim());
        role.setRoleName(role.getRoleName().trim());
        if (role.getStatus() != null && role.getStatus() != 0 && role.getStatus() != 1) {
            throw new BusinessException("角色状态只能是0或1");
        }
    }

    private void validateRoleUpdate(SysRoleEntity role) {
        if (role == null || role.getId() == null) {
            throw new BusinessException("角色ID不能为空");
        }
        if (role.getRoleName() != null) {
            if (!StringUtils.hasText(role.getRoleName())) {
                throw new BusinessException("角色名称不能为空");
            }
            role.setRoleName(role.getRoleName().trim());
        }
        if (role.getStatus() != null && role.getStatus() != 0 && role.getStatus() != 1) {
            throw new BusinessException("角色状态只能是0或1");
        }
    }

    private void validatePermissions(List<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return;
        }
        List<SysPermissionEntity> permissions = permissionMapper.selectBatchIds(permissionIds);
        Set<Long> existingIds = permissions == null ? Set.of() : permissions.stream()
                .map(SysPermissionEntity::getId)
                .collect(Collectors.toSet());
        List<Long> missingIds = permissionIds.stream()
                .filter(id -> !existingIds.contains(id))
                .toList();
        if (!missingIds.isEmpty()) {
            throw new BusinessException("权限不存在: " + missingIds);
        }
    }

    private void rejectRoleReferencedBySod(Long roleId) {
        List<SysSodConstraintEntity> constraints = sodConstraintMapper.selectList(null);
        for (SysSodConstraintEntity constraint : constraints) {
            try {
                List<Long> roleIds = objectMapper.readValue(
                        constraint.getRoleSet(), new TypeReference<List<Long>>() {
                        });
                if (roleIds.stream().anyMatch(roleId::equals)) {
                    throw new BusinessException("角色仍被SoD约束引用，不能删除: " + constraint.getConstraintName());
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException("SoD约束配置无效，无法安全删除角色: " + constraint.getId());
            }
        }
    }

    @Override
    public List<Long> getRolePermissionIds(Long roleId) {
        Set<Long> permissionIds = rolePermissionMapper.selectPermissionIdsByRoleIds(Set.of(roleId));
        return permissionIds != null ? new ArrayList<>(permissionIds) : Collections.emptyList();
    }

    @Override
    public List<SysPermissionEntity> getRolePermissions(Long roleId) {
        List<Long> permissionIds = getRolePermissionIds(roleId);
        if (permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return permissionMapper.selectBatchIds(permissionIds);
    }

    @Override
    public List<Long> getDescendantRoleIds(Long roleId) {
        Set<Long> descendants = new HashSet<>();
        Queue<Long> queue = new LinkedList<>();
        queue.add(roleId);
        
        while (!queue.isEmpty()) {
            Long currentId = queue.poll();
            // 查询以当前角色为祖先角色的所有后代角色
            LambdaQueryWrapper<SysRoleInheritanceEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysRoleInheritanceEntity::getAncestorId, currentId);
            List<SysRoleInheritanceEntity> children = roleInheritanceMapper.selectList(wrapper);
            
            for (SysRoleInheritanceEntity child : children) {
                Long descendantId = child.getDescendantId();
                if (!descendants.contains(descendantId)) {
                    descendants.add(descendantId);
                    queue.add(descendantId);
                }
            }
        }
        return new ArrayList<>(descendants);
    }
}

