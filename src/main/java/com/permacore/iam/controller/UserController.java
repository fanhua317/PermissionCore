package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.permacore.iam.annotation.OperLog;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.vo.PageVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.domain.vo.ResultCode;
import com.permacore.iam.domain.vo.UserCreateVO;
import com.permacore.iam.service.SysDeptService;
import com.permacore.iam.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户管理控制器
 */
@Tag(name = "用户管理", description = "用户增删改查及状态管理")
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final SysDeptService deptService;


    /**
     * 分页查询用户
     * 权限要求：system:user:query 或拥有任何管理权限
     */
    // @PreAuthorize("hasAuthority('system:user:query')")
    @Operation(summary = "分页查询用户", description = "根据条件分页查询用户列表")
    @GetMapping("/page")
    public Result<PageVO<SysUserEntity>> page(@ModelAttribute com.permacore.iam.domain.vo.UserQueryVO query) {
        log.info("Requesting user list: {}", query);
        try {
            int pageNo = query.getPageNo() == null ? 1 : query.getPageNo();
            int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();
            Page<SysUserEntity> page = new Page<>(pageNo, pageSize);
            LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<>();

            // 条件查询
            if (StringUtils.hasText(query.getUsername())) {
                wrapper.like(SysUserEntity::getUsername, query.getUsername());
            }
            if (StringUtils.hasText(query.getNickname())) {
                wrapper.like(SysUserEntity::getNickname, query.getNickname());
            }
            if (query.getStatus() != null) {
                wrapper.eq(SysUserEntity::getStatus, query.getStatus());
            }
            if (query.getDeptId() != null) {
                wrapper.eq(SysUserEntity::getDeptId, query.getDeptId());
            }

            wrapper.eq(SysUserEntity::getDelFlag, 0)
                    .orderByDesc(SysUserEntity::getCreateTime);

            Page<SysUserEntity> resultPage = userService.page(page, wrapper);
            log.info("User list query success, total: {}", resultPage.getTotal());

            // 填充部门名称
            List<SysUserEntity> records = resultPage.getRecords();
            if (!records.isEmpty()) {
                List<Long> deptIds = records.stream()
                        .map(SysUserEntity::getDeptId)
                        .filter(id -> id != null && id > 0)
                        .distinct()
                        .collect(Collectors.toList());
                if (!deptIds.isEmpty()) {
                    Map<Long, String> deptNameMap = deptService.listByIds(deptIds).stream()
                            .collect(Collectors.toMap(
                                    d -> d.getId(),
                                    d -> d.getDeptName(),
                                    (a, b) -> a
                            ));
                    records.forEach(u -> {
                        if (u.getDeptId() != null && deptNameMap.containsKey(u.getDeptId())) {
                            u.setDeptName(deptNameMap.get(u.getDeptId()));
                        }
                    });
                }
            }

            // 密码不返回前端
            records.forEach(u -> u.setPassword(null));

            return Result.success(PageVO.of(resultPage));
        } catch (Exception e) {
            log.error("Failed to query user list", e);
            throw e;
        }
    }

    /**
     * 根据ID查询用户
     */
    @Operation(summary = "获取用户详情", description = "根据ID获取用户详细信息")
    @PreAuthorize("hasAuthority('system:user:query')")
    @GetMapping("/{id}")
    public Result<SysUserEntity> getById(@PathVariable Long id) {
        SysUserEntity user = userService.getById(id);
        if (user == null || Byte.valueOf((byte)1).equals(user.getDelFlag())) {
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
    @Operation(summary = "创建用户", description = "新增用户")
    @OperLog(title = "创建用户", businessType = 1)
    @PreAuthorize("hasAuthority('user:add')")
    @PostMapping
    public Result<Void> create(@Validated @RequestBody UserCreateVO createVO) {
        // 校验用户名
        if (!StringUtils.hasText(createVO.getUsername())) {
            return Result.error("用户名不能为空", "username字段为空或未提供");
        }
        if (createVO.getUsername().length() < 3 || createVO.getUsername().length() > 20) {
            return Result.error("用户名长度需在3-20个字符之间", "username长度不符合要求: " + createVO.getUsername().length());
        }
        // 检查用户名重复
        if (userService.usernameExists(createVO.getUsername())) {
            return Result.error("用户名已被使用，请换一个", "用户名重复: " + createVO.getUsername());
        }
        // 校验昵称
        if (!StringUtils.hasText(createVO.getNickname())) {
            return Result.error("昵称不能为空", "nickname字段为空或未提供");
        }

        SysUserEntity user = new SysUserEntity();
        user.setUsername(createVO.getUsername());
        user.setNickname(createVO.getNickname());
        user.setEmail(createVO.getEmail());
        user.setPhone(createVO.getPhone());
        // 默认密码
        String rawPassword = StringUtils.hasText(createVO.getPassword()) ? createVO.getPassword() : "123456";
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setDeptId(createVO.getDeptId() != null ? createVO.getDeptId() : 0L);
        user.setStatus((byte) 1);
        user.setDelFlag((byte) 0);

        if (userService.save(user)) {
            log.info("创建用户成功: username={}", user.getUsername());
            return Result.success();
        }
        return Result.error("创建用户失败，请稍后重试", "UserService.save() 返回 false, username=" + createVO.getUsername());
    }

    /**
     * 更新用户
     */
    @Operation(summary = "更新用户", description = "更新用户信息")
    @OperLog(title = "更新用户", businessType = 2)
    @PreAuthorize("hasAuthority('user:edit')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysUserEntity user) {
        if (id == null) {
            return Result.error("ID不能为空");
        }
        user.setId(id);
        // 不允许修改密码和用户名
        user.setPassword(null);
        user.setUsername(null);

        if (userService.updateById(user)) {
            // 清除缓存
            userService.clearUserCache(user.getId());
            log.info("更新用户成功: userId={}", user.getId());
            return Result.success();
        }
        return Result.error("更新失败");
    }

    /**
     * 删除用户
     * 需要 user:delete 权限
     */
    @Operation(summary = "删除用户", description = "逻辑删除用户")
    @OperLog(title = "删除用户", businessType = 3)
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
     * 分配角色给用户 (POST)
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
     * 分配角色给用户 (PUT - 前端使用此接口)
     */
    @PreAuthorize("hasAuthority('user:assignRole')")
    @PutMapping("/{userId}/roles")
    public Result<Void> updateRoles(@PathVariable Long userId, 
            @RequestBody com.permacore.iam.domain.vo.AssignRolesVO vo) {
        userService.assignRoles(userId, vo.getRoleIds());
        // 清除权限缓存
        userService.clearUserCache(userId);
        log.info("更新角色: userId={}, roles={}", userId, vo.getRoleIds());
        return Result.success();
    }

    /**
     * 获取用户拥有的角色（返回完整角色对象列表）
     */
    @GetMapping("/{userId}/roles")
    public Result<List<com.permacore.iam.domain.entity.SysRoleEntity>> getUserRoles(@PathVariable Long userId) {
        List<com.permacore.iam.domain.entity.SysRoleEntity> roles = userService.getUserRoles(userId);
        return Result.success(roles);
    }

    /**
     * 重置用户密码
     * 权限：user:resetPassword
     */
    @PreAuthorize("hasAuthority('user:resetPassword')")
    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestParam(name = "newPassword", defaultValue = "123456") String newPassword) {
        SysUserEntity user = userService.getById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        if (userService.updateById(user)) {
            userService.clearUserCache(id);
            log.info("重置密码成功: userId={}", id);
            return Result.success();
        }
        return Result.error("重置密码失败");
    }
}