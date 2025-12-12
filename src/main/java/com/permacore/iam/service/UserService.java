package com.permacore.iam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.permacore.iam.domain.entity.UserEntity;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

public interface UserService extends UserDetailsService {
    Page<UserEntity> page(Page<UserEntity> page, LambdaQueryWrapper<UserEntity> wrapper);
    UserEntity getById(Long id);
    boolean usernameExists(String username);
    boolean save(UserEntity user);
    boolean updateById(UserEntity user);
    boolean removeById(Long id);
    boolean removeByIds(List<Long> ids);
    void clearUserCache(Long userId);
    void assignRoles(Long userId, List<Long> roleIds);
    List<Long> getUserRoleIds(Long userId);
}
