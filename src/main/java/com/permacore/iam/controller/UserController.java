package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.permacore.iam.annotation.OperLog;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.vo.PageVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.domain.vo.ResultCode;
import com.permacore.iam.domain.vo.UserCreateVO;
import com.permacore.iam.domain.vo.UserUpdateVO;
import com.permacore.iam.domain.vo.ResetPasswordVO;
import com.permacore.iam.service.SysDeptService;
import com.permacore.iam.service.UserService;
import com.permacore.iam.service.AuthorizationStateService;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.mapper.SysDeptMapper;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.domain.entity.SysDeptEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.validation.Valid;

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
    private final AuthorizationStateService authorizationStateService;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;

    /**
     * 分页查询用户
     * 权限要求：system:user:query
     */
    @Operation(summary = "分页查询用户", description = "根据条件分页查询用户列表")
    @PreAuthorize("hasAuthority('system:user:query')")
    @GetMapping("/page")
    public Result<PageVO<SysUserEntity>> page(@ModelAttribute com.permacore.iam.domain.vo.UserQueryVO query) {
        log.info("Requesting user list: {}", query);
        try {
            int pageNo = query.getPageNo() == null ? 1 : Math.max(1, query.getPageNo());
            int pageSize = query.getPageSize() == null ? 10 : Math.min(100, Math.max(1, query.getPageSize()));
            String username = StringUtils.hasText(query.getUsername()) ? query.getUsername() : null;
            String nickname = StringUtils.hasText(query.getNickname()) ? query.getNickname() : null;
            boolean sharedKeyword = username != null && nickname != null && username.equals(nickname);

            long total = userMapper.countUserPage(
                    username, nickname, sharedKeyword, query.getStatus(), query.getDeptId());
            long offset = ((long) pageNo - 1L) * pageSize;
            List<SysUserEntity> records = offset < total
                    ? userMapper.selectUserPage(
                            offset, pageSize, username, nickname, sharedKeyword,
                            query.getStatus(), query.getDeptId())
                    : List.of();

            Page<SysUserEntity> resultPage = new Page<>(pageNo, pageSize);
            resultPage.setTotal(total);
            resultPage.setRecords(records);
            log.info("User list query success, total: {}", resultPage.getTotal());

            // 填充部门名称
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
                                    (a, b) -> a));
                    records.forEach(u -> {
                        if (u.getDeptId() != null && deptNameMap.containsKey(u.getDeptId())) {
                            u.setDeptName(deptNameMap.get(u.getDeptId()));
                        }
                    });
                }
            }

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
        if (user == null || Byte.valueOf((byte) 1).equals(user.getDelFlag())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
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
    @org.springframework.transaction.annotation.Transactional
    public Result<Void> create(@Valid @RequestBody UserCreateVO createVO) {
        if (createVO.getDeptId() != null && createVO.getDeptId() != 0L) {
            deptMapper.lockAllDeptIds();
            validateAssignableDept(createVO.getDeptId());
        }
        String username = createVO.getUsername().trim();
        // 检查用户名重复
        if (userService.usernameExists(username)) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS, "用户名已被使用，请换一个");
        }

        SysUserEntity user = new SysUserEntity();
        user.setUsername(username);
        user.setNickname(createVO.getNickname().trim());
        user.setEmail(createVO.getEmail());
        user.setPhone(createVO.getPhone());
        user.setPassword(passwordEncoder.encode(createVO.getPassword()));
        user.setDeptId(createVO.getDeptId() != null ? createVO.getDeptId() : 0L);
        user.setStatus(createVO.getStatus() == null ? (byte) 1 : createVO.getStatus().byteValue());
        user.setDelFlag((byte) 0);

        if (userService.save(user)) {
            log.info("创建用户成功: username={}", user.getUsername());
            return Result.success();
        }
        throw new BusinessException(ResultCode.ERROR, "创建用户失败，请稍后重试");
    }

    /**
     * 更新用户
     */
    @Operation(summary = "更新用户", description = "更新用户信息")
    @OperLog(title = "更新用户", businessType = 2)
    @PreAuthorize("hasAuthority('user:edit')")
    @PutMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateVO vo) {
        roleMapper.lockAllRoleIds();
        if (id == null) {
            throw new BusinessException("ID不能为空");
        }
        SysUserEntity user = userService.getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        long targetDeptId = vo.getDeptId() == null
                ? (user.getDeptId() == null ? 0L : user.getDeptId())
                : vo.getDeptId();
        byte targetStatus = vo.getStatus() == null
                ? (user.getStatus() == null ? (byte) 1 : user.getStatus())
                : vo.getStatus().byteValue();
        if (targetDeptId != 0L && (vo.getDeptId() != null || targetStatus == 1)) {
            deptMapper.lockAllDeptIds();
            validateAssignableDept(targetDeptId);
        }
        if (java.util.Objects.equals(currentUserId(), id) && Integer.valueOf(0).equals(vo.getStatus())) {
            throw new BusinessException("不能禁用当前登录用户");
        }
        LambdaUpdateWrapper<SysUserEntity> update = new LambdaUpdateWrapper<SysUserEntity>()
                .eq(SysUserEntity::getId, id)
                .eq(SysUserEntity::getDelFlag, (byte) 0);
        boolean changed = false;
        if (vo.getNickname() != null) {
            update.set(SysUserEntity::getNickname, vo.getNickname().trim());
            changed = true;
        }
        if (vo.getEmail() != null) {
            update.set(SysUserEntity::getEmail, vo.getEmail());
            changed = true;
        }
        if (vo.getPhone() != null) {
            update.set(SysUserEntity::getPhone, vo.getPhone());
            changed = true;
        }
        if (vo.getDeptId() != null) {
            update.set(SysUserEntity::getDeptId, vo.getDeptId());
            changed = true;
        }
        if (vo.getStatus() != null) {
            update.set(SysUserEntity::getStatus, vo.getStatus().byteValue());
            changed = true;
        }

        if (!changed || userService.update(update)) {
            authorizationStateService.invalidateUsers(List.of(id));
            log.info("更新用户成功: userId={}", id);
            return Result.success();
        }
        throw new BusinessException(ResultCode.ERROR, "更新失败");
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
        if (java.util.Objects.equals(currentUserId(), id)) {
            throw new BusinessException("不能删除当前登录用户");
        }
        userService.deleteUser(id);
        log.info("删除用户成功: userId={}", id);
        return Result.success();
    }

    /**
     * 批量删除用户
     */
    @PreAuthorize("hasAuthority('user:delete')")
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        if (ids != null && ids.contains(currentUserId())) {
            throw new BusinessException("批量删除不能包含当前登录用户");
        }
        userService.deleteUsers(ids);
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
        log.info("分配角色: userId={}, roles={}", userId, roleIds);
        return Result.success();
    }

    /**
     * 分配角色给用户 (PUT - 前端使用此接口)
     */
    @PreAuthorize("hasAuthority('user:assignRole')")
    @PutMapping("/{userId}/roles")
    public Result<Void> updateRoles(@PathVariable Long userId,
            @Valid @RequestBody com.permacore.iam.domain.vo.AssignRolesVO vo) {
        userService.assignRoles(userId, vo.getRoleIds());
        log.info("更新角色: userId={}, roles={}", userId, vo.getRoleIds());
        return Result.success();
    }

    /**
     * 获取用户拥有的角色（返回完整角色对象列表）
     */
    @PreAuthorize("hasAnyAuthority('system:user:query','user:assignRole')")
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
    @org.springframework.transaction.annotation.Transactional
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordVO vo) {
        roleMapper.lockAllRoleIds();
        SysUserEntity user = userService.getById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        boolean updated = userService.update(new LambdaUpdateWrapper<SysUserEntity>()
                .eq(SysUserEntity::getId, id)
                .eq(SysUserEntity::getDelFlag, (byte) 0)
                .set(SysUserEntity::getPassword, passwordEncoder.encode(vo.getNewPassword())));
        if (updated) {
            authorizationStateService.invalidateUsers(List.of(id));
            log.info("重置密码成功: userId={}", id);
            return Result.success();
        }
        throw new BusinessException(ResultCode.ERROR, "重置密码失败");
    }

    private Long currentUserId() {
        org.springframework.security.core.Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof Number ? ((Number) principal).longValue() : null;
    }

    private void validateAssignableDept(Long deptId) {
        Map<Long, SysDeptEntity> deptMap = deptService.list(
                        new LambdaQueryWrapper<SysDeptEntity>().eq(SysDeptEntity::getDelFlag, (byte) 0))
                .stream().collect(Collectors.toMap(SysDeptEntity::getId, dept -> dept, (a, b) -> a));
        java.util.Set<Long> visited = new java.util.HashSet<>();
        Long cursor = deptId;
        while (cursor != null && cursor != 0L && visited.add(cursor)) {
            SysDeptEntity dept = deptMap.get(cursor);
            if (dept == null || !Byte.valueOf((byte) 1).equals(dept.getStatus())) {
                throw new BusinessException("部门或其上级部门不存在、已删除或未启用: " + deptId);
            }
            cursor = dept.getParentId();
        }
        if (cursor != null && cursor != 0L) {
            throw new BusinessException("部门树中存在环，请先修复数据");
        }
    }
}
