package com.permacore.iam.controller;

import com.permacore.iam.domain.vo.LoginVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.utils.JwtUtil;
import com.permacore.iam.utils.RedisCacheUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RedisCacheUtil redisCacheUtil;

    /**
     * 登录接口（实际逻辑在JwtAuthenticationFilter中）
     * 此处仅作为文档展示
     */
    @PostMapping("/login")
    public Result<Void> login(@RequestBody LoginVO loginVO) {
        // 实际认证由JwtAuthenticationFilter处理
        return Result.error("请使用POST /api/auth/login直接访问，此接口仅为文档");
    }

    /**
     * 刷新Token
     */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody Map<String, String> params) {
        String refreshToken = params.get("refreshToken");
        if (refreshToken == null) {
            throw new BusinessException("RefreshToken不能为空");
        }

        try {
            // 验证RefreshToken
            Long userId = jwtUtil.getUserIdFromToken(refreshToken);

            // 生成新的AccessToken
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", userId);

            String newAccessToken = jwtUtil.generateAccessToken(claims);

            Map<String, Object> tokenMap = new HashMap<>();
            tokenMap.put("accessToken", newAccessToken);
            tokenMap.put("tokenType", "Bearer");
            tokenMap.put("expiresIn", jwtUtil.getTokenRemainTime(newAccessToken));

            log.info("Token刷新成功: userId={}", userId);
            return Result.success(tokenMap);

        } catch (Exception e) {
            log.warn("Token刷新失败: {}", e.getMessage());
            throw new BusinessException("RefreshToken无效或已过期");
        }
    }

    /**
     * 登出（强制失效JWT）
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token != null) {
            try {
                Long userId = jwtUtil.getUserIdFromToken(token);
                // 删除JWT版本号，使Token失效
                redisCacheUtil.deleteJwtVersion(userId);
                // 清除权限缓存
                redisCacheUtil.deleteUserPermissions(userId);

                log.info("用户登出: userId={}", userId);
                return Result.success();
            } catch (Exception e) {
                log.warn("登出处理失败: {}", e.getMessage());
            }
        }
        return Result.success();
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> getUserInfo(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            throw new BusinessException("未登录");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", userId);
        userInfo.put("permissions", SecurityContextHolder.getContext()
                .getAuthentication().getAuthorities());

        return Result.success(userInfo);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}