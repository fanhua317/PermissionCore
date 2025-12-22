package com.permacore.iam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.domain.entity.SysRoleEntity;
import com.permacore.iam.domain.entity.SysSodConstraintEntity;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.entity.SysUserRoleEntity;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.mapper.SysUserRoleMapper;
import com.permacore.iam.service.SysSodConstraintService;
import com.permacore.iam.utils.RedisCacheUtil;
import com.permacore.iam.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 用户详情服务（Spring Security核心接口）
 * 负责从数据库加载用户信息和权限，并提供基本的用户 CRUD/分页等方法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUserEntity> implements UserDetailsService, com.permacore.iam.service.UserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PermissionService permissionService;
    private final RedisCacheUtil redisCacheUtil;
    private final SysSodConstraintService sodConstraintService;
    private final ObjectMapper objectMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (userMapper == null) {
            log.error("SysUserMapper is null in UserServiceImpl!");
            throw new InternalAuthenticationServiceException("SysUserMapper injection failed");
        }
        // 通过用户名查找用户
        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserEntity::getUsername, username);
        SysUserEntity user = userMapper.selectOne(wrapper);

        if (user == null || Byte.valueOf((byte)1).equals(user.getDelFlag())) {
            log.warn("用户不存在: username={}", username);
            throw new UsernameNotFoundException("用户不存在");
        }
        if (Byte.valueOf((byte)0).equals(user.getStatus())) {
            log.warn("用户已被禁用: username={}", username);
            throw new UsernameNotFoundException("用户已被禁用");
        }
        log.info("加载用户信息: userId={}, username={}", user.getId(), user.getUsername());

        Set<String> permissions = getUserPermissionsWithCache(user.getId());
        List<GrantedAuthority> authorities = permissions.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());

        SecurityUser securityUser = new SecurityUser(user, authorities);

        log.info("UserDetails created: username={}, password={}", securityUser.getUsername(), securityUser.getPassword());
        return securityUser;
    }

    public Set<String> getUserPermissionsWithCache(Long userId) {
        Set<String> permissions = redisCacheUtil.getUserPermissions(userId);
        if (permissions != null) {
            return permissions;
        }
        permissions = permissionService.getUserPermissions(userId);
        if (permissions == null) {
            permissions = new HashSet<>();
        }
        redisCacheUtil.setUserPermissions(userId, permissions, 30, TimeUnit.MINUTES);
        log.debug("加载用户权限: userId={}, permissionCount={}", userId, permissions.size());
        return permissions;
    }

    public boolean usernameExists(String username) {
        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserEntity::getUsername, username);
        return userMapper.selectCount(wrapper) > 0;
    }

    public void clearUserCache(Long userId) {
        redisCacheUtil.deleteUserPermissions(userId);
        redisCacheUtil.deleteJwtVersion(userId);
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        // RBAC3: 校验静态互斥约束 (SSD - Static Separation of Duty)
        if (roleIds != null && roleIds.size() > 1) {
            checkSsdConstraints(roleIds);
        }
        
        // 先删除原有角色关联
        userRoleMapper.deleteByUserId(userId);
        // 插入新的角色关联
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                SysUserRoleEntity userRole = new SysUserRoleEntity();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userRoleMapper.insert(userRole);
            }
        }
        // 清除用户权限缓存，使新角色权限立即生效
        clearUserCache(userId);
        log.info("角色分配完成: userId={}, roleIds={}", userId, roleIds);
    }
    
    /**
     * 校验静态职责分离约束 (SSD)
     * 如果要分配的角色集合违反了任何SSD约束，则抛出异常
     */
    private void checkSsdConstraints(List<Long> roleIds) {
        // 获取所有静态互斥约束 (constraint_type = 1)
        List<SysSodConstraintEntity> ssdConstraints = sodConstraintService.list(
            new LambdaQueryWrapper<SysSodConstraintEntity>()
                .eq(SysSodConstraintEntity::getConstraintType, (byte) 1)
        );
        
        Set<Long> roleIdSet = new HashSet<>(roleIds);
        
        for (SysSodConstraintEntity constraint : ssdConstraints) {
            try {
                // 解析互斥角色ID集合 (JSON数组格式)
                List<Long> mutexRoleIds = objectMapper.readValue(
                    constraint.getRoleSet(), 
                    new TypeReference<List<Long>>() {}
                );
                
                // 计算交集数量
                long conflictCount = mutexRoleIds.stream()
                    .filter(roleIdSet::contains)
                    .count();
                
                // 如果用户要分配的角色中有2个或更多互斥角色，则违反约束
                if (conflictCount >= 2) {
                    log.warn("SSD约束冲突: constraint={}, conflictRoles={}", 
                        constraint.getConstraintName(), 
                        mutexRoleIds.stream().filter(roleIdSet::contains).collect(Collectors.toList()));
                    throw new RuntimeException("角色分配违反职责分离约束: " + constraint.getConstraintName());
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                log.error("解析SoD约束失败: constraintId={}, error={}", constraint.getId(), e.getMessage());
            }
        }
    }

    @Override
    public List<Long> getUserRoleIds(Long userId) {
        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(userId);
        return roleIds != null ? roleIds : Collections.emptyList();
    }

    @Override
    public List<SysRoleEntity> getUserRoles(Long userId) {
        List<Long> roleIds = getUserRoleIds(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return roleMapper.selectBatchIds(roleIds);
    }

    @Override
    public Page<SysUserEntity> page(Page<SysUserEntity> page, LambdaQueryWrapper<SysUserEntity> wrapper) {
        return super.page(page, wrapper);
    }
}

/*
 * 非 Lombok 版本示例：
 * public class UserServiceImpl extends ServiceImpl<SysUserMapper, SysUserEntity>
 *         implements UserDetailsService, com.permacore.iam.service.UserService {
 *     private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
 *     private final SysUserMapper userMapper;
 *     private final PermissionService permissionService;
 *     private final RedisCacheUtil redisCacheUtil;
 *
 *     public UserServiceImpl(SysUserMapper userMapper, PermissionService permissionService, RedisCacheUtil redisCacheUtil) {
 *         this.userMapper = userMapper;
 *         this.permissionService = permissionService;
 *         this.redisCacheUtil = redisCacheUtil;
 *     }
 *     // 其余方法保持不变
 * }
 */
