package com.permacore.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.permacore.iam.domain.entity.SysUserEntity;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

public interface UserService extends IService<SysUserEntity>, UserDetailsService {
    Page<SysUserEntity> page(Page<SysUserEntity> page, LambdaQueryWrapper<SysUserEntity> wrapper);
    boolean usernameExists(String username);
    void clearUserCache(Long userId);
    void assignRoles(Long userId, List<Long> roleIds);
    List<Long> getUserRoleIds(Long userId);
}
