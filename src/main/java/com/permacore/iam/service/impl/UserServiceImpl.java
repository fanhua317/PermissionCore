package com.permacore.iam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.permacore.iam.domain.entity.UserEntity;
import com.permacore.iam.mapper.UserMapper;
import com.permacore.iam.utils.RedisCacheUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户详情服务（Spring Security核心接口）
 * 负责从数据库加载用户信息和权限
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;
    private final PermissionService permissionService;
    private final RedisCacheUtil redisCacheUtil;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // username实际存的是用户ID（字符串形式）
        Long userId = Long.parseLong(username);

        // 1. 查询用户信息
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getDelFlag() == 1) {
            log.warn("用户不存在: userId={}", userId);
            throw new UsernameNotFoundException("用户不存在");
        }

        if (user.getStatus() == 0) {
            log.warn("用户已被禁用: userId={}", userId);
            throw new UsernameNotFoundException("用户已被禁用");
        }

        log.info("加载用户信息: userId={}, username={}", userId, user.getUsername());

        // 2. 查询用户权限（优先走缓存）
        Set<String> permissions = getUserPermissionsWithCache(userId);

        // 3. 转换为Spring Security权限对象
        List<GrantedAuthority> authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        // 4. 返回UserDetails对象
        // 注意：username字段存用户ID，password存加密密码
        return User.builder()
                .username(String.valueOf(userId)) // Principal存用户ID
                .password(user.getPassword())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }

    /**
     * 获取用户权限（带缓存）
     */
    public Set<String> getUserPermissionsWithCache(Long userId) {
        // 1. 先查缓存
        Set<String> permissions = redisCacheUtil.getUserPermissions(userId);
        if (permissions != null) {
            return permissions;
        }

        // 2. 查数据库
        permissions = permissionService.getUserPermissions(userId);
        if (permissions == null) {
            permissions = new HashSet<>();
        }

        // 3. 写入缓存（30分钟）
        redisCacheUtil.setUserPermissions(userId, permissions, 30, TimeUnit.MINUTES);

        log.debug("加载用户权限: userId={}, permissionCount={}", userId, permissions.size());
        return permissions;
    }
}