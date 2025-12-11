package com.permacore.iam.controller;

import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.security.handler.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限测试控制器
 * 演示 @PreAuthorize 注解的多种用法
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class PermissionTestController {

    /**
     * 测试：需要 user:view 权限
     */
    @PreAuthorize("hasAuthority('user:view')")
    @GetMapping("/user-view")
    public Result<String> testUserView() {
        return Result.success("您有 user:view 权限");
    }

    /**
     * 测试：需要 user:delete 权限（最高权限）
     */
    @PreAuthorize("hasAuthority('user:delete')")
    @GetMapping("/user-delete")
    public Result<String> testUserDelete() {
        return Result.success("您有 user:delete 权限");
    }

    /**
     * 测试：需要任意一种权限
     */
    @PreAuthorize("hasAnyAuthority('user:add','user:edit')")
    @GetMapping("/user-any")
    public Result<String> testUserAny() {
        return Result.success("您有 user:add 或 user:edit 权限");
    }

    /**
     * 测试：需要同时拥有多种权限
     */
    @PreAuthorize("hasAuthority('user:add') and hasAuthority('user:edit')")
    @GetMapping("/user-both")
    public Result<String> testUserBoth() {
        return Result.success("您同时拥有 user:add 和 user:edit 权限");
    }

    /**
     * 测试：仅允许ADMIN角色访问
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin-only")
    public Result<String> testAdminOnly() {
        return Result.success("您是ADMIN角色");
    }

    /**
     * 测试：匿名访问（无需登录）
     */
    @GetMapping("/public")
    public Result<String> testPublic() {
        return Result.success("这是公开接口，无需登录");
    }

    /**
     * 测试：表达式 - 只能查看自己的信息或拥有admin权限
     */
    @PreAuthorize("hasAuthority('user:view') or authentication.name == #userId.toString()")
    @GetMapping("/self-or-admin")
    public Result<String> testSelfOrAdmin(Long userId) {
        return Result.success("您有权查看 userId=" + userId + " 的信息");
    }

    /**
     * 测试：抛出业务异常
     */
    @PreAuthorize("hasAuthority('user:add')")
    @GetMapping("/business-error")
    public Result<Void> testBusinessError() {
        throw new BusinessException("这是一个业务异常");
    }
}