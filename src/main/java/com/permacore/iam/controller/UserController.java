package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.permacore.iam.domain.entity.UserEntity;
import com.permacore.iam.domain.vo.PageVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.domain.vo.UserCreateVO;
import com.permacore.iam.service.impl.UserServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserServiceImpl userService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 分页查询用户
     * 权限要求：system:user:query 或拥有任何管理权限
     */
    @PreAuthorize("hasAuthority('system:user:query')")
    @GetMapping("/page")
    public Result<PageVO<UserEntity>> page(@RequestParam(defaultValue = "1") Integer pageNo,
                                           @RequestParam(defaultValue = "10") Integer pageSize,
                                           @RequestParam(required = false) String username,
                                           @RequestParam(required = false) String nickname,
                                           @RequestParam(required = false) Integer status) {

        Page<UserEntity> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();

        // 条件查询
        if (StringUtils.hasText(username)) {
            wrapper.like(UserEntity::getUsername, username);
        }
        if (StringUtils.hasText(nickname)) {
            wrapper.like(UserEntity::getNickname, nickname);
        }
        if (status != null) {
            wrapper.eq(UserEntity::getStatus, status);
        }

        wrapper.eq(UserEntity::getDelFlag, 0)
                .orderByDesc(UserEntity::getCreateTime);

        Page<UserEntity> resultPage = userService.page(page, wrapper);

        return Result.success(PageVO.of(resultPage));
    }

    /**
     * 根据ID查询用户
     */
    @PreAuthorize("hasAuthority('system:user:query')")
    @GetMapping("/{id}")
    public Result<UserEntity> getById(@PathVariable Long id) {
        UserEntity user = userService.getById(id);
        if (user == null || user.getDelFlag() == 1) {
            return Result.error("用户不存在");
        }
        // 密码不返回前端
        user.setPassword(null);
        return Result.success(user);
    }

    /**
     * 创建用户
     * 权限：user:add
     */
    @PreAuthorize("hasAuthority('user:add')")
    @PostMapping
    public Result<Void> create(@Validated @RequestBody UserCreateVO createVO) {
        // 检查用户名重复
        if (userService.usernameExists(createVO.getUsername())) {
            return Result.error(ResultCode.USERNAME_EXISTS);
        }

        UserEntity user = new UserEntity();
        user.setUsername(createVO.getUsername());
        user.setNickname(createVO.getNickname());
        user.setEmail(createVO.getEmail());
        user.setPhone(createVO.getPhone());
        user.setDeptId(createVO.getDeptId());

        // 密码加密
        user.setPassword(passwordEncoder.encode(createVO.getPassword()));

        userService.save(user);
        log.info("创建用户成功: userId={}, username={}", user.getId(), user.getUsername());
        return Result.success();
    }

    /**
     * 更新用户
     */
    @PreAuthorize("hasAuthority('user:edit')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UserEntity user) {
        user.setId(id);
        // 防止更新密码和用户名
        user.setPassword(null);
        user.setUsername(null);

        boolean success = userService.updateById(user);
        if (success) {
            // 更新成功后清除权限缓存
            userService.clearUserCache(id);
            log.info("更新用户成功: userId={}", id);
        }
        return Result.success();
    }

    /**
     * 删除用户
     * 需要 user:delete 权限
     */
    @PreAuthorize("hasAuthority('user:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        boolean success = userService.removeById(id);
        if (success) {
            // 删除用户后清除缓存
            userService.clearUserCache(id);
            log.info("删除用户成功: userId={}", id);
        }
        return Result.success();
    }

    /**
     * 批量删除用户
     */
    @PreAuthorize("hasAuthority('user:delete')")
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        userService.removeByIds(ids);
        // 批量清除缓存
        ids.forEach(userService::clearUserCache);
        log.info("批量删除用户: ids={}", ids);
        return Result.success();
    }

    /**
     * 分配角色给用户
     * 需要 user:assignRole 权限
     */
    @PreAuthorize("hasAuthority('user:assignRole')")
    @PostMapping("/{userId}/roles")
    public Result<Void> assignRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        userService.assignRoles(userId, roleIds);
        // 清除权限缓存
        userService.clearUserCache(userId);
        log.info("分配角色: userId={}, roles={}", userId, roleIds);
        return Result.success();
    }

    /**
     * 获取用户拥有的角色
     */
    @PreAuthorize("hasAuthority('system:user:query')")
    @GetMapping("/{userId}/roles")
    public Result<List<Long>> getUserRoles(@PathVariable Long userId) {
        List<Long> roleIds = userService.getUserRoleIds(userId);
        return Result.success(roleIds);
    }
}