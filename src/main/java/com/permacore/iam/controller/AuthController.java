package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.permacore.iam.domain.entity.SysLoginLogEntity;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.vo.LoginVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.domain.vo.ResultCode;
import com.permacore.iam.domain.vo.RoleSessionUpdateVO;
import com.permacore.iam.domain.vo.SessionRoleStateVO;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.service.RoleSessionService;
import com.permacore.iam.service.AuthorizationStateService;
import com.permacore.iam.service.SysLoginLogService;
import com.permacore.iam.utils.JwtUtil;
import com.permacore.iam.utils.RedisCacheUtil;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Tag(name = "认证管理", description = "登录、Token、当前用户和会话角色接口")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Value("${app.upload.avatar-path:uploads/avatars}")
    private String avatarUploadPath;

    private final JwtUtil jwtUtil;
    private final RedisCacheUtil redisCacheUtil;
    private final SysUserMapper sysUserMapper;
    private final SysLoginLogService loginLogService;
    private final PasswordEncoder passwordEncoder;
    private final RoleSessionService roleSessionService;
    private final AuthorizationStateService authorizationStateService;
    private final SysRoleMapper roleMapper;
    private final String dummyPasswordHash;

    public AuthController(JwtUtil jwtUtil,
            RedisCacheUtil redisCacheUtil,
            @Qualifier("sysUserMapper") SysUserMapper sysUserMapper,
            SysLoginLogService loginLogService,
            PasswordEncoder passwordEncoder,
            RoleSessionService roleSessionService,
            AuthorizationStateService authorizationStateService,
            SysRoleMapper roleMapper) {
        this.jwtUtil = jwtUtil;
        this.redisCacheUtil = redisCacheUtil;
        this.sysUserMapper = sysUserMapper;
        this.loginLogService = loginLogService;
        this.passwordEncoder = passwordEncoder;
        this.roleSessionService = roleSessionService;
        this.authorizationStateService = authorizationStateService;
        this.roleMapper = roleMapper;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回Token和默认激活角色")
    @PostMapping("/login")
    @org.springframework.transaction.annotation.Transactional(noRollbackFor = BusinessException.class)
    public Result<Map<String, Object>> login(@RequestBody LoginVO loginVO, HttpServletRequest request) {
        if (loginVO == null || loginVO.getUsername() == null || loginVO.getUsername().isBlank()
                || loginVO.getPassword() == null || loginVO.getPassword().isBlank()) {
            throw new BusinessException("用户名和密码不能为空");
        }
        if (loginVO.getPassword().length() > 72) {
            throw new BusinessException("密码长度不能超过72位");
        }
        if (loginVO.getUsername().length() > 50) {
            throw new BusinessException("用户名长度不能超过50位");
        }
        String username = loginVO.getUsername().trim();
        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        try {
            roleMapper.lockAllRoleIdsShared();
            SysUserEntity user = findUserByUsername(username);
            String passwordHash = user == null ? dummyPasswordHash : user.getPassword();
            boolean passwordMatches = passwordEncoder.matches(loginVO.getPassword(), passwordHash);
            if (user == null || !Byte.valueOf((byte) 1).equals(user.getStatus()) || !passwordMatches) {
                recordLoginLog(username, ipAddress, userAgent, (byte) 0, "用户名或密码错误");
                throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
            }

            SessionRoleStateVO state = roleSessionService.buildDefaultState(user.getId());
            Map<String, Object> tokenMap = issueTokens(user, state, null);
            recordLoginLog(username, ipAddress, userAgent, (byte) 1, "登录成功");
            return Result.success(tokenMap);
        } catch (BusinessException e) {
            if (!Integer.valueOf(ResultCode.UNAUTHORIZED.getCode()).equals(e.getCode())) {
                recordLoginLog(username, ipAddress, userAgent, (byte) 0, e.getMessage());
            }
            throw e;
        } catch (DataAccessException | IllegalStateException e) {
            log.error("登录依赖服务不可用: username={}", username, e);
            recordLoginLog(username, ipAddress, userAgent, (byte) 0, "服务不可用");
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "登录服务暂不可用，请稍后重试");
        } catch (RuntimeException e) {
            log.error("登录处理失败: username={}", username, e);
            recordLoginLog(username, ipAddress, userAgent, (byte) 0, "系统错误");
            throw new BusinessException(ResultCode.ERROR, "登录失败，请稍后重试");
        }
    }

    @Operation(summary = "刷新Token", description = "使用RefreshToken获取新的AccessToken，并保持当前激活角色")
    @PostMapping("/refresh")
    @org.springframework.transaction.annotation.Transactional
    public Result<Map<String, Object>> refresh(@RequestBody Map<String, String> params) {
        String refreshToken = params == null ? null : params.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("RefreshToken不能为空");
        }

        Claims refreshClaims;
        Long userId;
        try {
            refreshClaims = jwtUtil.parseToken(refreshToken);
            if (!jwtUtil.isRefreshToken(refreshClaims)) {
                throw new IllegalArgumentException("Token 类型错误");
            }
            userId = Long.parseLong(refreshClaims.getSubject());
        } catch (IllegalArgumentException e) {
            log.warn("Token刷新失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "RefreshToken无效或已过期");
        }

        try {
            roleMapper.lockAllRoleIdsShared();
            SysUserEntity user = requireActiveUser(sysUserMapper.selectAuthenticationStateById(userId));
            if (!matchesAuthVersion(refreshClaims, user)) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "RefreshToken 已失效");
            }
            String currentSessionId = redisCacheUtil.getJwtVersion(userId);
            String refreshSessionId = jwtUtil.getSessionId(refreshClaims);
            if (currentSessionId == null || refreshSessionId == null
                    || !currentSessionId.equals(refreshSessionId)) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "RefreshToken 已失效");
            }

            boolean hasActiveRoleClaim = refreshClaims.containsKey("activeRoleIds");
            List<Long> activeRoleIds = roleSessionService.parseRoleIdsClaim(refreshClaims.get("activeRoleIds"));
            SessionRoleStateVO state = hasActiveRoleClaim
                    ? roleSessionService.buildState(userId, activeRoleIds)
                    : roleSessionService.buildDefaultState(userId);
            return Result.success(issueTokens(user, state, refreshSessionId));
        } catch (BusinessException e) {
            throw e;
        } catch (DataAccessException | IllegalStateException e) {
            log.error("Token刷新依赖服务不可用: userId={}", userId, e);
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "认证服务暂不可用，请稍后重试");
        } catch (RuntimeException e) {
            log.error("Token刷新处理失败: userId={}", userId, e);
            throw new BusinessException(ResultCode.ERROR, "Token刷新失败，请稍后重试");
        }
    }

    @Operation(summary = "用户登出", description = "使当前Token失效")
    @PostMapping("/logout")
    @org.springframework.transaction.annotation.Transactional
    public Result<Void> logout(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token != null) {
            try {
                Claims claims = jwtUtil.parseToken(token);
                Long userId = Long.parseLong(claims.getSubject());
                roleMapper.lockAllRoleIds();
                SysUserEntity authorizationState = sysUserMapper.selectAuthorizationStateById(userId);
                String currentSessionId = redisCacheUtil.getJwtVersion(userId);
                String tokenSessionId = jwtUtil.getSessionId(claims);
                if (authorizationState != null && matchesAuthVersion(claims, authorizationState)
                        && tokenSessionId != null && tokenSessionId.equals(currentSessionId)) {
                    authorizationStateService.invalidateUsers(List.of(userId));
                }
                log.info("用户登出: userId={}", userId);
            } catch (DataAccessException | IllegalStateException e) {
                log.error("登出依赖服务不可用", e);
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "退出服务暂不可用，请稍后重试");
            } catch (Exception e) {
                log.error("登出处理失败", e);
                throw new BusinessException(ResultCode.ERROR, "退出失败，请重试");
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
        return Result.success();
    }

    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户信息和当前会话权限")
    @GetMapping("/info")
    @org.springframework.transaction.annotation.Transactional
    public Result<Map<String, Object>> getUserInfo(HttpServletRequest request) {
        String token = requireToken(request);
        Claims claims = jwtUtil.parseToken(token);
        Long userId = Long.parseLong(claims.getSubject());
        roleMapper.lockAllRoleIdsShared();
        SysUserEntity user = requireActiveUser(sysUserMapper.selectAuthenticationStateById(userId));
        if (!matchesAuthVersion(claims, user)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "会话已失效，请重新登录");
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
    @org.springframework.transaction.annotation.Transactional
    public Result<SessionRoleStateVO> getSessionRoles(HttpServletRequest request) {
        String token = requireToken(request);
        Claims claims = jwtUtil.parseToken(token);
        Long userId = Long.parseLong(claims.getSubject());
        roleMapper.lockAllRoleIdsShared();
        SysUserEntity user = requireActiveUser(sysUserMapper.selectAuthenticationStateById(userId));
        if (!matchesAuthVersion(claims, user)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "会话已失效，请重新登录");
        }
        return Result.success(buildStateFromClaims(userId, claims));
    }

    @Operation(summary = "切换会话角色", description = "切换当前激活角色，并返回新的Token和权限")
    @PutMapping("/session-roles")
    @org.springframework.transaction.annotation.Transactional
    public Result<Map<String, Object>> updateSessionRoles(@RequestBody RoleSessionUpdateVO vo,
            HttpServletRequest request) {
        String token = requireToken(request);
        Claims currentClaims = jwtUtil.parseToken(token);
        Long userId = Long.parseLong(currentClaims.getSubject());
        roleMapper.lockAllRoleIdsShared();
        SysUserEntity user = requireActiveUser(sysUserMapper.selectAuthenticationStateById(userId));
        if (!matchesAuthVersion(currentClaims, user)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "会话已失效，请重新登录");
        }
        String currentSessionId = jwtUtil.getSessionId(currentClaims);
        if (currentSessionId == null || currentSessionId.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "会话已失效，请重新登录");
        }

        List<Long> requestedRoleIds = vo == null ? List.of() : vo.getActiveRoleIds();
        SessionRoleStateVO state = roleSessionService.buildState(userId, requestedRoleIds);
        return Result.success(issueTokens(user, state, currentSessionId));
    }

    @Operation(summary = "修改密码", description = "修改当前登录用户密码")
    @PostMapping("/change-password")
    @org.springframework.transaction.annotation.Transactional
    public Result<Void> changePassword(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String token = requireToken(request);
        String oldPassword = params == null ? null : params.get("oldPassword");
        String newPassword = params == null ? null : params.get("newPassword");

        if (oldPassword == null || oldPassword.isBlank()) {
            throw new BusinessException("旧密码不能为空");
        }
        if (oldPassword.length() > 72) {
            throw new BusinessException("旧密码长度不能超过72位");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException("新密码不能为空");
        }
        if (newPassword.length() < 8) {
            throw new BusinessException("新密码长度不能少于8位");
        }
        if (newPassword.length() > 72) {
            throw new BusinessException("新密码长度不能超过72位");
        }

        Claims passwordClaims = jwtUtil.parseToken(token);
        Long userId = Long.parseLong(passwordClaims.getSubject());
        roleMapper.lockAllRoleIds();
        SysUserEntity user = requireActiveUser(sysUserMapper.selectAuthenticationStateById(userId));
        if (!matchesAuthVersion(passwordClaims, user)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "会话已失效，请重新登录");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("旧密码错误");
        }

        int updated = sysUserMapper.update(null, new LambdaUpdateWrapper<SysUserEntity>()
                .eq(SysUserEntity::getId, userId)
                .eq(SysUserEntity::getDelFlag, (byte) 0)
                .set(SysUserEntity::getPassword, passwordEncoder.encode(newPassword)));
        if (updated != 1) {
            throw new BusinessException(ResultCode.ERROR, "修改密码失败");
        }
        authorizationStateService.invalidateUsers(List.of(userId));
        return Result.success();
    }

    @Operation(summary = "上传头像", description = "上传当前用户头像图片")
    @PostMapping("/upload-avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        String token = requireToken(request);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException("图片大小不能超过2MB");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        try {
            DecodedImage decodedImage = decodeAvatar(file);
            Path uploadDir = Path.of(avatarUploadPath);
            if (!uploadDir.isAbsolute()) {
                uploadDir = Path.of(System.getProperty("user.dir")).resolve(uploadDir);
            }
            uploadDir = uploadDir.toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);

            String newFilename = "avatar_" + userId + "_" + UUID.randomUUID().toString().substring(0, 8)
                    + "." + decodedImage.format();
            Path filePath = uploadDir.resolve(newFilename).normalize();
            if (!filePath.startsWith(uploadDir)) {
                throw new IOException("非法上传路径");
            }
            if (!ImageIO.write(decodedImage.image(), decodedImage.format(), filePath.toFile())) {
                throw new IOException("图片编码失败");
            }

            Map<String, String> result = new HashMap<>();
            result.put("avatarUrl", "/uploads/avatars/" + newFilename);
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        } catch (IOException e) {
            log.error("头像上传失败", e);
            throw new BusinessException(ResultCode.ERROR, "头像上传失败，请稍后重试");
        }
    }

    @Operation(summary = "更新个人信息", description = "更新当前登录用户昵称、邮箱、手机号")
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody Map<String, String> params, HttpServletRequest request) {
        String token = requireToken(request);
        Long userId = jwtUtil.getUserIdFromToken(token);
        if (params == null) {
            throw new BusinessException("个人信息不能为空");
        }
        LambdaUpdateWrapper<SysUserEntity> update = new LambdaUpdateWrapper<SysUserEntity>()
                .eq(SysUserEntity::getId, userId)
                .eq(SysUserEntity::getDelFlag, (byte) 0);
        boolean changed = false;
        if (params.containsKey("nickname")) {
            String nickname = params.get("nickname");
            if (nickname == null || nickname.isBlank() || nickname.length() > 50) {
                throw new BusinessException("昵称不能为空且不能超过50个字符");
            }
            update.set(SysUserEntity::getNickname, nickname.trim());
            changed = true;
        }
        if (params.containsKey("email")) {
            String email = params.get("email");
            if (email != null && email.length() > 100) {
                throw new BusinessException("邮箱不能超过100个字符");
            }
            if (email != null && !email.isBlank()
                    && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                throw new BusinessException("邮箱格式不正确");
            }
            update.set(SysUserEntity::getEmail, email == null || email.isBlank() ? null : email.trim());
            changed = true;
        }
        if (params.containsKey("phone")) {
            String phone = params.get("phone");
            if (phone != null && phone.length() > 20) {
                throw new BusinessException("手机号不能超过20个字符");
            }
            update.set(SysUserEntity::getPhone, phone == null || phone.isBlank() ? null : phone.trim());
            changed = true;
        }
        if (changed && sysUserMapper.update(null, update) != 1) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return Result.success();
    }

    private Map<String, Object> issueTokens(SysUserEntity user, SessionRoleStateVO state,
            String expectedSessionId) {
        Map<String, Object> claims = roleSessionService.buildJwtClaims(
                user.getId(), user.getUsername(), user.getNickname(), state);
        claims.put("authVersion", user.getAuthVersion() == null ? 0L : user.getAuthVersion());
        if (user.getGlobalAuthVersion() == null) {
            throw new IllegalStateException("全局授权版本未初始化");
        }
        claims.put("globalAuthVersion", user.getGlobalAuthVersion());
        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateAccessToken(claims, sessionId);
        String refreshToken = jwtUtil.generateRefreshToken(claims, sessionId);

        long sessionTtl = jwtUtil.getTokenRemainTime(refreshToken);
        boolean sessionStored;
        if (expectedSessionId == null) {
            redisCacheUtil.setJwtVersion(user.getId(), sessionId, sessionTtl, TimeUnit.SECONDS);
            sessionStored = true;
        } else {
            sessionStored = redisCacheUtil.rotateJwtVersion(
                    user.getId(), expectedSessionId, sessionId, sessionTtl, TimeUnit.SECONDS);
        }
        if (!sessionStored) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "会话已更新，请重新登录");
        }

        Map<String, Object> tokenMap = new HashMap<>();
        tokenMap.put("accessToken", accessToken);
        tokenMap.put("refreshToken", refreshToken);
        tokenMap.put("tokenType", "Bearer");
        tokenMap.put("expiresIn", jwtUtil.getTokenRemainTime(accessToken));
        roleSessionService.appendSessionState(tokenMap, state);
        return tokenMap;
    }

    private SessionRoleStateVO buildStateFromClaims(Long userId, Claims claims) {
        boolean hasActiveRoleClaim = claims.containsKey("activeRoleIds");
        List<Long> activeRoleIds = roleSessionService.parseRoleIdsClaim(claims.get("activeRoleIds"));
        if (!hasActiveRoleClaim) {
            return roleSessionService.buildDefaultState(userId);
        }
        try {
            return roleSessionService.buildState(userId, activeRoleIds);
        } catch (BusinessException e) {
            return roleSessionService.buildDefaultState(userId);
        }
    }

    private SysUserEntity findUserByUsername(String username) {
        return sysUserMapper.selectAuthenticationStateByUsername(username);
    }

    private SysUserEntity requireActiveUser(SysUserEntity user) {
        if (user == null || Byte.valueOf((byte) 1).equals(user.getDelFlag())
                || !Byte.valueOf((byte) 1).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或已被禁用");
        }
        return user;
    }

    private boolean matchesAuthVersion(Claims claims, SysUserEntity user) {
        Object claimValue = claims.get("authVersion");
        Object globalClaimValue = claims.get("globalAuthVersion");
        if (!(claimValue instanceof Number) || !(globalClaimValue instanceof Number)
                || user.getGlobalAuthVersion() == null) {
            return false;
        }
        long currentVersion = user.getAuthVersion() == null ? 0L : user.getAuthVersion();
        return ((Number) claimValue).longValue() == currentVersion
                && ((Number) globalClaimValue).longValue() == user.getGlobalAuthVersion();
    }

    private DecodedImage decodeAvatar(MultipartFile file) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
            if (input == null) {
                throw new IllegalArgumentException("无法识别图片文件");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("只支持 PNG 或 JPEG 图片");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String detectedFormat = reader.getFormatName().toLowerCase();
                String outputFormat = switch (detectedFormat) {
                    case "png" -> "png";
                    case "jpeg", "jpg" -> "jpg";
                    default -> throw new IllegalArgumentException("只支持 PNG 或 JPEG 图片");
                };
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 4096 || height > 4096
                        || (long) width * height > 16_777_216L) {
                    throw new IllegalArgumentException("图片尺寸不能超过 4096×4096");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IllegalArgumentException("图片内容无效");
                }
                return new DecodedImage(image, outputFormat);
            } finally {
                reader.dispose();
            }
        }
    }

    private record DecodedImage(BufferedImage image, String format) {
    }

    private String requireToken(HttpServletRequest request) {
        String token = resolveToken(request);
        if (token == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
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
        return request.getRemoteAddr();
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
