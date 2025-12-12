package com.permacore.iam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.entity.UserEntity;
import com.permacore.iam.mapper.SysUserMapper;
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
    private final PermissionService permissionService;
    private final RedisCacheUtil redisCacheUtil;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
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
        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
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

    public Page<UserEntity> page(Page<UserEntity> page, LambdaQueryWrapper<UserEntity> wrapper) {
        Page<SysUserEntity> sysPage = new Page<>(page.getCurrent(), page.getSize());
        LambdaQueryWrapper<SysUserEntity> sysWrapper = new LambdaQueryWrapper<>();
        sysWrapper.setEntity(null);
        this.page(sysPage, (LambdaQueryWrapper<SysUserEntity>) (Object) wrapper);
        Page<UserEntity> out = new Page<>();
        out.setCurrent(sysPage.getCurrent());
        out.setSize(sysPage.getSize());
        out.setTotal(sysPage.getTotal());
        out.setRecords(sysPage.getRecords().stream().map(this::toUserEntity).collect(Collectors.toList()));
        return out;
    }

    public UserEntity getById(Long id) {
        SysUserEntity s = userMapper.selectById(id);
        return s == null ? null : toUserEntity(s);
    }

    public boolean usernameExists(String username) {
        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserEntity::getUsername, username);
        return userMapper.selectCount(wrapper) > 0;
    }

    public boolean save(UserEntity user) {
        return userMapper.insert(toSysUserEntity(user)) > 0;
    }

    public boolean updateById(UserEntity user) {
        return userMapper.updateById(toSysUserEntity(user)) > 0;
    }

    public boolean removeById(Long id) {
        return userMapper.deleteById(id) > 0;
    }

    public boolean removeByIds(List<Long> ids) {
        return userMapper.deleteBatchIds(ids) > 0;
    }

    public void clearUserCache(Long userId) {
        redisCacheUtil.deleteUserPermissions(userId);
        redisCacheUtil.deleteJwtVersion(userId);
    }

    public void assignRoles(Long userId, List<Long> roleIds) {
    }

    public List<Long> getUserRoleIds(Long userId) {
        return Collections.emptyList();
    }

    private UserEntity toUserEntity(SysUserEntity s) {
        UserEntity u = new UserEntity();
        u.setId(s.getId());
        u.setUsername(s.getUsername());
        u.setPassword(s.getPassword());
        u.setNickname(s.getNickname());
        u.setEmail(s.getEmail());
        u.setPhone(s.getPhone());
        u.setDeptId(s.getDeptId());
        u.setStatus(s.getStatus());
        u.setCreateBy(s.getCreateBy());
        u.setCreateTime(s.getCreateTime());
        u.setUpdateBy(s.getUpdateBy());
        u.setUpdateTime(s.getUpdateTime());
        u.setRemark(s.getRemark());
        u.setDelFlag(s.getDelFlag());
        return u;
    }

    private SysUserEntity toSysUserEntity(UserEntity user) {
        SysUserEntity s = new SysUserEntity();
        s.setId(user.getId());
        s.setUsername(user.getUsername());
        s.setPassword(user.getPassword());
        s.setNickname(user.getNickname());
        s.setEmail(user.getEmail());
        s.setPhone(user.getPhone());
        s.setDeptId(user.getDeptId());
        s.setStatus(user.getStatus());
        s.setCreateBy(user.getCreateBy());
        s.setCreateTime(user.getCreateTime());
        s.setUpdateBy(user.getUpdateBy());
        s.setUpdateTime(user.getUpdateTime());
        s.setRemark(user.getRemark());
        s.setDelFlag(user.getDelFlag());
        return s;
    }
}