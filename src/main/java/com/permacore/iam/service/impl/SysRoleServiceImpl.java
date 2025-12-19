package com.permacore.iam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.permacore.iam.domain.entity.SysPermissionEntity;
import com.permacore.iam.domain.entity.SysRoleEntity;
import com.permacore.iam.domain.entity.SysRoleInheritanceEntity;
import com.permacore.iam.domain.entity.SysRolePermissionEntity;
import com.permacore.iam.mapper.SysPermissionMapper;
import com.permacore.iam.mapper.SysRoleInheritanceMapper;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.mapper.SysRolePermissionMapper;
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

    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRoleInheritanceMapper roleInheritanceMapper;

    @Override
    public boolean saveRole(SysRoleEntity role) {
        return super.save(role);
    }

    @Override
    public boolean updateRole(SysRoleEntity role) {
        return super.updateById(role);
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
        // 删除角色权限关联
        rolePermissionMapper.deleteByRoleId(id);
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
        log.info("级联删除角色完成: roleId={}", id);
    }

    @Override
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        // 先删除原有权限
        rolePermissionMapper.deleteByRoleId(roleId);
        // 批量插入新权限
        if (permissionIds != null && !permissionIds.isEmpty()) {
            List<SysRolePermissionEntity> records = permissionIds.stream()
                    .map(permId -> {
                        SysRolePermissionEntity rp = new SysRolePermissionEntity();
                        rp.setRoleId(roleId);
                        rp.setPermissionId(permId);
                        return rp;
                    })
                    .collect(Collectors.toList());
            rolePermissionMapper.insertBatch(records);
        }
        log.info("权限分配完成: roleId={}, permissionIds={}", roleId, permissionIds);
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
    @Transactional
    public void setRoleInheritance(Long roleId, Long parentId) {
        // 检查是否已存在
        LambdaQueryWrapper<SysRoleInheritanceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleInheritanceEntity::getDescendantId, roleId)
               .eq(SysRoleInheritanceEntity::getAncestorId, parentId);
        if (roleInheritanceMapper.selectCount(wrapper) > 0) {
            log.info("角色继承关系已存在: descendantId={}, ancestorId={}", roleId, parentId);
            return;
        }
        // 创建继承关系
        SysRoleInheritanceEntity inheritance = new SysRoleInheritanceEntity();
        inheritance.setDescendantId(roleId);
        inheritance.setAncestorId(parentId);
        roleInheritanceMapper.insert(inheritance);
        log.info("设置角色继承: descendantId={}, ancestorId={}", roleId, parentId);
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

