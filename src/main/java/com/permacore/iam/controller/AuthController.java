package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.vo.LoginVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.utils.JwtUtil;
import com.permacore.iam.utils.RedisCacheUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RedisCacheUtil redisCacheUtil;
    private final SysUserMapper sysUserMapper;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          RedisCacheUtil redisCacheUtil,
                          @Qualifier("sysUserMapper") SysUserMapper sysUserMapper) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.redisCacheUtil = redisCacheUtil;
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 登录接口
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginVO loginVO) {
        log.info("用户登录请求: username={}", loginVO.getUsername());

        try {
            // 认证
            UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(loginVO.getUsername(), loginVO.getPassword());
            Authentication authentication = authenticationManager.authenticate(authToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 通过用户名查询用户ID
            LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysUserEntity::getUsername, loginVO.getUsername());
            SysUserEntity user = sysUserMapper.selectOne(wrapper);
            Long userId = user != null ? user.getId() : 1L;

            // 生成Token
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", userId);
            claims.put("username", loginVO.getUsername());

            String accessToken = jwtUtil.generateAccessToken(claims);
            String refreshToken = jwtUtil.generateRefreshToken(claims);

            // 存储JWT版本到Redis
            redisCacheUtil.setJwtVersion(userId, jwtUtil.getVersionFromToken(accessToken));

            Map<String, Object> tokenMap = new HashMap<>();
            tokenMap.put("accessToken", accessToken);
            tokenMap.put("refreshToken", refreshToken);
            tokenMap.put("tokenType", "Bearer");
            tokenMap.put("expiresIn", jwtUtil.getTokenRemainTime(accessToken));

            log.info("用户登录成功: username={}", loginVO.getUsername());
            return Result.success(tokenMap);

        } catch (BadCredentialsException e) {
            log.warn("登录失败: 用户名或密码错误, username={}", loginVO.getUsername());
            throw new BusinessException("用户名或密码错误");
        } catch (Exception e) {
            log.error("登录异常: {}", e.getMessage());
            throw new BusinessException("登录失败: " + e.getMessage());
        }
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