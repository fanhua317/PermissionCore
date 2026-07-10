package com.permacore.iam.controller;

import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.domain.vo.SessionRoleStateVO;
import com.permacore.iam.domain.vo.RoleSessionUpdateVO;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.mapper.SysRoleMapper;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.service.RoleSessionService;
import com.permacore.iam.service.SysLoginLogService;
import com.permacore.iam.service.AuthorizationStateService;
import com.permacore.iam.utils.JwtUtil;
import com.permacore.iam.utils.RedisCacheUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthControllerRefreshTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysLoginLogService loginLogService;
    @Mock
    private AuthorizationStateService authorizationStateService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RoleSessionService roleSessionService;
    @Mock
    private SysRoleMapper roleMapper;

    private JwtUtil jwtUtil;
    private RedisCacheUtil cache;
    private AuthController controller;
    private SysUserEntity activeUser;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 7200L);
        jwtUtil.initializeSecretKey();

        cache = new RedisCacheUtil();
        ReflectionTestUtils.setField(cache, "redisEnabled", false);
        controller = new AuthController(jwtUtil, cache, userMapper,
                loginLogService, passwordEncoder, roleSessionService, authorizationStateService, roleMapper);

        activeUser = new SysUserEntity();
        activeUser.setId(1L);
        activeUser.setUsername("admin");
        activeUser.setStatus((byte) 1);
        activeUser.setDelFlag((byte) 0);
        activeUser.setAuthVersion(0L);
        when(userMapper.selectById(1L)).thenReturn(activeUser);
    }

    @Test
    void refreshRotatesSessionAndRejectsReplayAndAccessToken() {
        when(roleSessionService.parseRoleIdsClaim(any())).thenReturn(List.of());
        when(roleSessionService.buildDefaultState(anyLong())).thenReturn(new SessionRoleStateVO());
        when(roleSessionService.buildJwtClaims(anyLong(), any(), any(), any())).thenAnswer(invocation -> {
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", invocation.getArgument(0));
            claims.put("username", invocation.getArgument(1));
            return claims;
        });
        cache.setJwtVersion(1L, "old-session", 2, TimeUnit.HOURS);
        String refresh = jwtUtil.generateRefreshToken(Map.of("userId", 1L, "authVersion", 0L), "old-session");

        Result<Map<String, Object>> result = controller.refresh(Map.of("refreshToken", refresh));
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().get("accessToken")).isNotNull();
        assertThat(cache.getJwtVersion(1L)).isNotEqualTo("old-session");

        assertThatThrownBy(() -> controller.refresh(Map.of("refreshToken", refresh)))
                .isInstanceOf(BusinessException.class);

        String access = jwtUtil.generateAccessToken(Map.of("userId", 1L), cache.getJwtVersion(1L));
        assertThatThrownBy(() -> controller.refresh(Map.of("refreshToken", access)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void disabledUserCannotRefresh() {
        activeUser.setStatus((byte) 0);
        cache.setJwtVersion(1L, "disabled-session", 2, TimeUnit.HOURS);
        String refresh = jwtUtil.generateRefreshToken(
                Map.of("userId", 1L, "authVersion", 0L), "disabled-session");

        assertThatThrownBy(() -> controller.refresh(Map.of("refreshToken", refresh)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void refreshPreservesAnExplicitlyEmptyActiveRoleSet() {
        SessionRoleStateVO emptyState = new SessionRoleStateVO();
        when(roleSessionService.parseRoleIdsClaim(any())).thenReturn(List.of());
        when(roleSessionService.buildState(1L, List.of())).thenReturn(emptyState);
        when(roleSessionService.buildJwtClaims(anyLong(), any(), any(), any()))
                .thenReturn(new HashMap<>(Map.of("userId", 1L)));
        cache.setJwtVersion(1L, "empty-role-session", 2, TimeUnit.HOURS);
        String refresh = jwtUtil.generateRefreshToken(
                Map.of("userId", 1L, "authVersion", 0L, "activeRoleIds", List.of()),
                "empty-role-session");

        controller.refresh(Map.of("refreshToken", refresh));

        verify(roleSessionService).buildState(1L, List.of());
        verify(roleSessionService, never()).buildDefaultState(1L);
    }

    @Test
    void refreshRejectsAStaleDatabaseAuthorizationVersion() {
        activeUser.setAuthVersion(2L);
        cache.setJwtVersion(1L, "stale-auth-session", 2, TimeUnit.HOURS);
        String refresh = jwtUtil.generateRefreshToken(
                Map.of("userId", 1L, "authVersion", 1L), "stale-auth-session");

        assertThatThrownBy(() -> controller.refresh(Map.of("refreshToken", refresh)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RefreshToken");
    }

    @Test
    void roleSwitchCannotReviveARequestRevokedAfterFilterValidation() {
        cache.setJwtVersion(1L, "role-switch-session", 2, TimeUnit.HOURS);
        String access = jwtUtil.generateAccessToken(
                Map.of("userId", 1L, "authVersion", 0L, "activeRoleIds", List.of()),
                "role-switch-session");
        activeUser.setAuthVersion(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + access);
        RoleSessionUpdateVO update = new RoleSessionUpdateVO();
        update.setActiveRoleIds(List.of());

        assertThatThrownBy(() -> controller.updateSessionRoles(update, request))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(401);
        verify(roleSessionService, never()).buildState(anyLong(), any());
    }
}
