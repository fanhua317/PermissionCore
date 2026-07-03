package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.permacore.iam.domain.entity.SysLoginLogEntity;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.vo.LoginVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.domain.vo.RoleSessionUpdateVO;
import com.permacore.iam.domain.vo.SessionRoleStateVO;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.service.RoleSessionService;
import com.permacore.iam.service.SysLoginLogService;
import com.permacore.iam.service.UserService;
import com.permacore.iam.utils.JwtUtil;
import com.permacore.iam.utils.RedisCacheUtil;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Tag(name = "认证管理", description = "登录、Token、当前用户和会话角色接口")
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
    private final RoleSessionService roleSessionService;

    public AuthController(AuthenticationManager authenticationManager,
            JwtUtil jwtUtil,
            RedisCacheUtil redisCacheUtil,
            @Qualifier("sysUserMapper") SysUserMapper sysUserMapper,
            SysLoginLogService loginLogService,
            UserService userService,
            PasswordEncoder passwordEncoder,
            RoleSessionService roleSessionService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.redisCacheUtil = redisCacheUtil;
        this.sysUserMapper = sysUserMapper;
        this.loginLogService = loginLogService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.roleSessionService = roleSessionService;
    }

    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回Token和默认激活角色")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginVO loginVO, HttpServletRequest request) {
        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    loginVO.getUsername(), loginVO.getPassword()));

            SysUserEntity user = findUserByUsername(loginVO.getUsername());
            if (user == null) {
                throw new BusinessException("用户不存在");
            }

            SessionRoleStateVO state = roleSessionService.buildDefaultState(user.getId());
            Map<String, Object> tokenMap = issueTokens(user, state);
            recordLoginLog(loginVO.getUsername(), ipAddress, userAgent, (byte) 1, "登录成功");
            return Result.success(tokenMap);
        } catch (BadCredentialsException e) {
            recordLoginLog(loginVO.getUsername(), ipAddress, userAgent, (byte) 0, "用户名或密码错误");
            throw new BusinessException("用户名或密码错误");
        } catch (Exception e) {
            recordLoginLog(loginVO.getUsername(), ipAddress, userAgent, (byte) 0, e.getMessage());
            throw new BusinessException("登录失败: " + e.getMessage());
        }
    }

    @Operation(summary = "刷新Token", description = "使用RefreshToken获取新的AccessToken，并保持当前激活角色")
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody Map<String, String> params) {
        String refreshToken = params.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("RefreshToken不能为空");
        }

        try {
            Claims refreshClaims = jwtUtil.parseToken(refreshToken);
            Long userId = Long.parseLong(refreshClaims.getSubject());
            SysUserEntity user = sysUserMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException("用户不存在");
            }

            List<Long> activeRoleIds = roleSessionService.parseRoleIdsClaim(refreshClaims.get("activeRoleIds"));
            SessionRoleStateVO state = activeRoleIds.isEmpty()
                    ? roleSessionService.buildDefaultState(userId)
                    : roleSessionService.buildState(userId, activeRoleIds);
            return Result.success(issueTokens(user, state));
        } catch (Exception e) {
            log.warn("Token刷新失败: {}", e.getMessage());
            throw new BusinessException("RefreshToken无效或已过期");
        }
    }

    @Operation(summary = "用户登出", description = "使当前Token失效")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token != null) {
            try {
                Long userId = jwtUtil.getUserIdFromToken(token);
                redisCacheUtil.deleteJwtVersion(userId);
                redisCacheUtil.deleteUserPermissions(userId);
                SecurityContextHolder.clearContext();
                log.info("用户登出: userId={}", userId);
            } catch (Exception e) {
                log.warn("登出处理失败: {}", e.getMessage());
            }
        }
        return Result.success();
    }

    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户信息和当前会话权限")
    @GetMapping("/info")
    public Result<Map<String, Object>> getUserInfo(HttpServletRequest request) {
        String token = requireToken(request);
        Claims claims = jwtUtil.parseToken(token);
        Long userId = Long.parseLong(claims.getSubject());
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        SessionRoleStateVO state = buildStateFromClaims(userId, claims);
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("userId", userId);
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("email", user.getEmail());
        userInfo.put("phone", user.getPhone());
        roleSessionService.appendSessionState(userInfo, state);
        return Result.success(userInfo);
    }

    @Operation(summary = "获取会话角色", description = "获取可激活角色、当前激活角色、有效角色、DSD冲突和当前权限")
    @GetMapping("/session-roles")
    public Result<SessionRoleStateVO> getSessionRoles(HttpServletRequest request) {
        String token = requireToken(request);
        Claims claims = jwtUtil.parseToken(token);
        Long userId = Long.parseLong(claims.getSubject());
        return Result.success(buildStateFromClaims(userId, claims));
    }

    @Operation(summary = "切换会话角色", description = "切换当前激活角色，并返回新的Token和权限")
    @PutMapping("/session-roles")
    public Result<Map<String, Object>> updateSessionRoles(@RequestBody RoleSessionUpdateVO vo,
            HttpServletRequest request) {
        String token = requireToken(request);
        Long userId = jwtUtil.getUserIdFromToken(token);
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        List<Long> requestedRoleIds = vo == null ? List.of() : vo.getActiveRoleIds();
        SessionRoleStateVO state = roleSessionService.buildState(userId, requestedRoleIds);
        return Result.success(issueTokens(user, state));
    }

    @Operation(summary = "修改密码", description = "修改当前登录用户密码")
    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String token = requireToken(request);
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
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return Result.error("旧密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(user);
        userService.clearUserCache(userId);
        redisCacheUtil.deleteJwtVersion(userId);
        return Result.success();
    }

    @Operation(summary = "上传头像", description = "上传当前用户头像图片")
    @PostMapping("/upload-avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        String token = requireToken(request);
        if (file == null || file.isEmpty()) {
            return Result.error("请选择要上传的文件");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.error("只能上传图片文件");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            return Result.error("图片大小不能超过2MB");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        try {
            java.io.File uploadDirFile = new java.io.File(avatarUploadPath);
            if (!uploadDirFile.isAbsolute()) {
                uploadDirFile = new java.io.File(System.getProperty("user.dir"), avatarUploadPath);
            }
            Path uploadDir = uploadDirFile.toPath();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = "avatar_" + userId + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
            Path filePath = uploadDir.resolve(newFilename);
            file.transferTo(Objects.requireNonNull(filePath.toFile()));

            Map<String, String> result = new HashMap<>();
            result.put("avatarUrl", "/uploads/avatars/" + newFilename);
            return Result.success(result);
        } catch (IOException e) {
            log.error("头像上传失败: {}", e.getMessage());
            return Result.error("头像上传失败");
        }
    }

    @Operation(summary = "更新个人信息", description = "更新当前登录用户昵称、邮箱、手机号")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String token = requireToken(request);
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
        return Result.success();
    }

    private Map<String, Object> issueTokens(SysUserEntity user, SessionRoleStateVO state) {
        Map<String, Object> claims = roleSessionService.buildJwtClaims(
                user.getId(), user.getUsername(), user.getNickname(), state);
        String accessToken = jwtUtil.generateAccessToken(claims);
        String refreshToken = jwtUtil.generateRefreshToken(claims);

        redisCacheUtil.setJwtVersion(user.getId(), Objects.requireNonNull(jwtUtil.getJtiFromToken(accessToken)),
                jwtUtil.getTokenRemainTime(accessToken), TimeUnit.SECONDS);

        Map<String, Object> tokenMap = new HashMap<>();
        tokenMap.put("accessToken", accessToken);
        tokenMap.put("refreshToken", refreshToken);
        tokenMap.put("tokenType", "Bearer");
        tokenMap.put("expiresIn", jwtUtil.getTokenRemainTime(accessToken));
        roleSessionService.appendSessionState(tokenMap, state);
        return tokenMap;
    }

    private SessionRoleStateVO buildStateFromClaims(Long userId, Claims claims) {
        List<Long> activeRoleIds = roleSessionService.parseRoleIdsClaim(claims.get("activeRoleIds"));
        if (activeRoleIds.isEmpty()) {
            return roleSessionService.buildDefaultState(userId);
        }
        try {
            return roleSessionService.buildState(userId, activeRoleIds);
        } catch (BusinessException e) {
            return roleSessionService.buildDefaultState(userId);
        }
    }

    private SysUserEntity findUserByUsername(String username) {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getUsername, username)
                .eq(SysUserEntity::getDelFlag, (byte) 0));
    }

    private String requireToken(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            throw new BusinessException("未登录");
        }
        return token;
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

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
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String parseBrowser(String userAgent) {
        if (userAgent == null) {
            return "Unknown";
        }
        if (userAgent.contains("Edge")) {
            return "Edge";
        }
        if (userAgent.contains("Chrome")) {
            return "Chrome";
        }
        if (userAgent.contains("Firefox")) {
            return "Firefox";
        }
        if (userAgent.contains("Safari")) {
            return "Safari";
        }
        if (userAgent.contains("MSIE") || userAgent.contains("Trident")) {
            return "IE";
        }
        return "Unknown";
    }

    private String parseOs(String userAgent) {
        if (userAgent == null) {
            return "Unknown";
        }
        if (userAgent.contains("Windows")) {
            return "Windows";
        }
        if (userAgent.contains("Mac")) {
            return "MacOS";
        }
        if (userAgent.contains("Linux")) {
            return "Linux";
        }
        if (userAgent.contains("Android")) {
            return "Android";
        }
        if (userAgent.contains("iPhone") || userAgent.contains("iPad")) {
            return "iOS";
        }
        return "Unknown";
    }
}
