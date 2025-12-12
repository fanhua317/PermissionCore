package com.permacore.iam.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.permacore.iam.domain.entity.RoleEntity;
import com.permacore.iam.domain.entity.SysRoleEntity;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.service.RoleService;
import com.permacore.iam.service.SysRoleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRoleEntity> implements SysRoleService, RoleService {

    @Override
    public boolean saveRole(RoleEntity role) {
        return super.save(toSysRole(role));
    }

    @Override
    public boolean updateRole(RoleEntity role) {
        return super.updateById(toSysRole(role));
    }

    @Override
    public List<RoleEntity> listRoles() {
        List<SysRoleEntity> sys = super.list();
        return sys.stream().map(s -> {
            RoleEntity r = new RoleEntity();
            r.setId(s.getId());
            r.setRoleKey(s.getRoleKey());
            r.setRoleName(s.getRoleName());
            r.setParentId(s.getParentId());
            r.setRoleType(s.getRoleType());
            r.setSortOrder(s.getSortOrder());
            r.setStatus(s.getStatus());
            r.setCreateBy(s.getCreateBy());
            r.setCreateTime(s.getCreateTime());
            r.setUpdateBy(s.getUpdateBy());
            r.setUpdateTime(s.getUpdateTime());
            r.setRemark(s.getRemark());
            r.setDelFlag(s.getDelFlag());
            return r;
        }).collect(Collectors.toList());
    }

    @Override
    public Page<RoleEntity> pageRoles(Integer pageNo, Integer pageSize, String roleName) {
        Page<SysRoleEntity> sysPage = new Page<>(pageNo, pageSize);
        super.page(sysPage);
        Page<RoleEntity> out = new Page<>();
        out.setCurrent(sysPage.getCurrent());
        out.setSize(sysPage.getSize());
        out.setTotal(sysPage.getTotal());
        out.setRecords((List<RoleEntity>)(List)sysPage.getRecords());
        return out;
    }

    @Override
    public Page<RoleEntity> page(Integer pageNo, Integer pageSize, String roleName) {
        return pageRoles(pageNo, pageSize, roleName);
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

    private SysRoleEntity toSysRole(RoleEntity role) {
        if (role == null) {
            return null;
        }
        SysRoleEntity s = new SysRoleEntity();
        s.setId(role.getId());
        s.setRoleKey(role.getRoleKey());
        s.setRoleName(role.getRoleName());
        s.setParentId(role.getParentId());
        s.setRoleType(role.getRoleType());
        s.setSortOrder(role.getSortOrder());
        s.setStatus(role.getStatus());
        s.setCreateBy(role.getCreateBy());
        s.setCreateTime(role.getCreateTime());
        s.setUpdateBy(role.getUpdateBy());
        s.setUpdateTime(role.getUpdateTime());
        s.setRemark(role.getRemark());
        s.setDelFlag(role.getDelFlag());
        return s;
    }
}
