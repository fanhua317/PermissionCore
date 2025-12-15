package com.permacore.iam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.permacore.iam.domain.entity.SysRoleEntity;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.service.SysRoleService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * <p>
 * 角色表 服务实现类
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Primary
@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRoleEntity> implements SysRoleService {

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
    public void deleteRoleCascade(Long id) {
        super.removeById(id);
    }

    @Override
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        // TODO
    }

    @Override
    public List<Long> getRolePermissionIds(Long roleId) {
        return java.util.Collections.emptyList();
    }

    @Override
    public void setRoleInheritance(Long roleId, Long parentId) {
        // TODO
    }

    @Override
    public List<Long> getDescendantRoleIds(Long roleId) {
        return java.util.Collections.emptyList();
    }
}

