package com.permacore.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.permacore.iam.domain.entity.SysRoleEntity;
import com.permacore.iam.domain.entity.SysUserEntity;

import java.util.List;

public interface UserService extends IService<SysUserEntity> {
    Page<SysUserEntity> page(Page<SysUserEntity> page, LambdaQueryWrapper<SysUserEntity> wrapper);
    boolean usernameExists(String username);
    void assignRoles(Long userId, List<Long> roleIds);

    void deleteUser(Long userId);

    void deleteUsers(List<Long> userIds);
    List<Long> getUserRoleIds(Long userId);
    
    /**
     * 获取用户的角色列表（完整角色对象）
     */
    List<SysRoleEntity> getUserRoles(Long userId);
}
