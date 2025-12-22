package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.permacore.iam.domain.entity.SysLoginLogEntity;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.vo.LoginVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.service.SysLoginLogService;
import com.permacore.iam.service.UserService;
import com.permacore.iam.utils.JwtUtil;
import com.permacore.iam.utils.RedisCacheUtil;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 认证控制器
 */
@Tag(name = "认证管理", description = "用户登录、注册、获取信息等接口")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Value("${app.upload.avatar-path:uploads/avatars}")
    private String avatarUploadPath;

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RedisCacheUtil redisCacheUtil;
    private final SysUserMapper sysUserMapper;
    private final SysLoginLogService loginLogService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          RedisCacheUtil redisCacheUtil,
                          @Qualifier("sysUserMapper") SysUserMapper sysUserMapper,
                          SysLoginLogService loginLogService,
                          UserService userService,
                          PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.redisCacheUtil = redisCacheUtil;
        this.sysUserMapper = sysUserMapper;
        this.loginLogService = loginLogService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 登录接口
     */
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回Token")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginVO loginVO, HttpServletRequest request) {
        log.info("用户登录请求: username={}", loginVO.getUsername());
        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

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

            // 记录登录成功日志
            recordLoginLog(loginVO.getUsername(), ipAddress, userAgent, (byte) 1, "登录成功");

            log.info("用户登录成功: username={}", loginVO.getUsername());
            return Result.success(tokenMap);

        } catch (BadCredentialsException e) {
            log.warn("登录失败: 用户名或密码错误, username={}", loginVO.getUsername());
            // 记录登录失败日志
            recordLoginLog(loginVO.getUsername(), ipAddress, userAgent, (byte) 0, "用户名或密码错误");
            throw new BusinessException("用户名或密码错误");
        } catch (Exception e) {
            log.error("登录异常: {}", e.getMessage());
            // 记录登录异常日志
            recordLoginLog(loginVO.getUsername(), ipAddress, userAgent, (byte) 0, e.getMessage());
            throw new BusinessException("登录失败: " + e.getMessage());
        }
    }

    /**
     * 记录登录日志
     */
    private void recordLoginLog(String username, String ipAddress, String userAgent, byte status, String message) {
        try {
            SysLoginLogEntity loginLog = new SysLoginLogEntity();
            loginLog.setUsername(username);
            loginLog.setIpAddress(ipAddress);
            loginLog.setLocation("本地");
            loginLog.setBrowser(parseBrowser(userAgent));
            loginLog.setOs(parseOs(userAgent));
            loginLog.setLoginTime(LocalDateTime.now());
            loginLog.setStatus(status);
            loginLog.setMessage(message);
            loginLogService.save(loginLog);
        } catch (Exception e) {
            log.error("记录登录日志失败: {}", e.getMessage());
        }
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理情况，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 解析浏览器
     */
    private String parseBrowser(String userAgent) {
        if (userAgent == null) return "Unknown";
        if (userAgent.contains("Edge")) return "Edge";
        if (userAgent.contains("Chrome")) return "Chrome";
        if (userAgent.contains("Firefox")) return "Firefox";
        if (userAgent.contains("Safari")) return "Safari";
        if (userAgent.contains("MSIE") || userAgent.contains("Trident")) return "IE";
        return "Unknown";
    }

    /**
     * 解析操作系统
     */
    private String parseOs(String userAgent) {
        if (userAgent == null) return "Unknown";
        if (userAgent.contains("Windows")) return "Windows";
        if (userAgent.contains("Mac")) return "MacOS";
        if (userAgent.contains("Linux")) return "Linux";
        if (userAgent.contains("Android")) return "Android";
        if (userAgent.contains("iPhone") || userAgent.contains("iPad")) return "iOS";
        return "Unknown";
    }

    /**
     * 刷新Token
     */
    @Operation(summary = "刷新Token", description = "使用RefreshToken获取新的AccessToken")
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody Map<String, String> params) {
        String refreshToken = params.get("refreshToken");
        if (refreshToken == null) {
            throw new BusinessException("RefreshToken不能为空");
        }

        try {
            // 验证RefreshToken
            Long userId = jwtUtil.getUserIdFromToken(refreshToken);

            SysUserEntity user = sysUserMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException("用户不存在");
            }

            // 重新生成权限（确保刷新后仍有权限）
            org.springframework.security.core.userdetails.UserDetails userDetails = userService.loadUserByUsername(user.getUsername());
            List<String> permissions = userDetails.getAuthorities().stream()
                    .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

            // 生成新的AccessToken
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", userId);
            claims.put("username", user.getUsername());
            claims.put("nickname", user.getNickname());
            claims.put("permissions", permissions);

            String newAccessToken = jwtUtil.generateAccessToken(claims);

            // 旋转 RefreshToken（更安全，也和前端拦截器契合）
            String newRefreshToken = jwtUtil.generateRefreshToken(userId);

            // 更新JWT版本号（否则新 token 会被版本校验拒绝）
            String jwtVersion = jwtUtil.getJtiFromToken(newAccessToken);
            redisCacheUtil.setJwtVersion(userId, jwtVersion,
                    jwtUtil.getTokenRemainTime(newAccessToken), TimeUnit.SECONDS);

            Map<String, Object> tokenMap = new HashMap<>();
            tokenMap.put("accessToken", newAccessToken);
            tokenMap.put("refreshToken", newRefreshToken);
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
    @Operation(summary = "用户登出", description = "使当前Token失效")
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
    @Operation(summary = "获取用户信息", description = "获取当前登录用户的详细信息")
    @GetMapping("/info")
    public Result<Map<String, Object>> getUserInfo(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            throw new BusinessException("未登录");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        Claims claims = jwtUtil.parseToken(token);

        String username = claims.get("username") != null ? claims.get("username").toString() : null;
        String nickname = claims.get("nickname") != null ? claims.get("nickname").toString() : null;

        // 始终从数据库获取最新用户信息以确保数据完整
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 使用数据库中的用户信息
        username = user.getUsername();
        nickname = user.getNickname();
        String email = user.getEmail();
        String phone = user.getPhone();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<String> permissions = authentication == null ? List.of() : authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", userId);
        userInfo.put("username", username);
        userInfo.put("nickname", nickname);
        userInfo.put("email", email);
        userInfo.put("phone", phone);
        userInfo.put("permissions", permissions);

        return Result.success(userInfo);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 修改当前用户密码
     */
    @Operation(summary = "修改密码", description = "修改当前登录用户的密码")
    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            throw new BusinessException("未登录");
        }

        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");

        if (oldPassword == null || oldPassword.isBlank()) {
            return Result.error("旧密码不能为空");
        }
        if (newPassword == null || newPassword.isBlank()) {
            return Result.error("新密码不能为空");
        }
        if (newPassword.length() < 6) {
            return Result.error("新密码长度不能少于6位");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return Result.error("旧密码错误");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(user);

        // 清除缓存，使当前token失效，要求重新登录
        userService.clearUserCache(userId);
        redisCacheUtil.deleteJwtVersion(userId);

        log.info("用户修改密码成功: userId={}", userId);
        return Result.success();
    }

    /**
     * 上传头像
     */
    @Operation(summary = "上传头像", description = "上传用户头像图片")
    @PostMapping("/upload-avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            throw new BusinessException("未登录");
        }

        if (file == null || file.isEmpty()) {
            return Result.error("请选择要上传的文件");
        }

        // 验证文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.error("只能上传图片文件");
        }

        // 验证文件大小（最大2MB）
        if (file.getSize() > 2 * 1024 * 1024) {
            return Result.error("图片大小不能超过2MB");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);

        try {
            // 确保上传目录存在 - 使用绝对路径
            java.io.File uploadDirFile = new java.io.File(avatarUploadPath);
            if (!uploadDirFile.isAbsolute()) {
                uploadDirFile = new java.io.File(System.getProperty("user.dir"), avatarUploadPath);
            }
            Path uploadDir = uploadDirFile.toPath();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = "avatar_" + userId + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

            // 保存文件
            Path filePath = uploadDir.resolve(newFilename);
            file.transferTo(filePath.toFile());

            // 返回访问路径
            String avatarUrl = "/uploads/avatars/" + newFilename;

            // 更新用户头像（如果用户表有avatar字段的话，这里可以更新）
            // 目前SysUserEntity没有avatar字段，可以在前端localStorage保存

            Map<String, String> result = new HashMap<>();
            result.put("avatarUrl", avatarUrl);

            log.info("用户上传头像成功: userId={}, file={}", userId, newFilename);
            return Result.success(result);

        } catch (IOException e) {
            log.error("头像上传失败: {}", e.getMessage());
            return Result.error("头像上传失败");
        }
    }

    /**
     * 更新当前用户信息（昵称、邮箱、手机）
     */
    @Operation(summary = "更新个人信息", description = "更新当前用户的昵称、邮箱、手机号")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            throw new BusinessException("未登录");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        if (params.containsKey("nickname")) {
            user.setNickname(params.get("nickname"));
        }
        if (params.containsKey("email")) {
            user.setEmail(params.get("email"));
        }
        if (params.containsKey("phone")) {
            user.setPhone(params.get("phone"));
        }

        sysUserMapper.updateById(user);
        userService.clearUserCache(userId);

        log.info("用户更新个人信息成功: userId={}", userId);
        return Result.success();
    }
}