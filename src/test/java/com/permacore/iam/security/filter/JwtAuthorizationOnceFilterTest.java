package com.permacore.iam.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.utils.JwtUtil;
import com.permacore.iam.utils.RedisCacheUtil;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.mapper.SysPermissionMapper;
import com.permacore.iam.domain.entity.SysUserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthorizationOnceFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptsCurrentAccessTokenButNeverAuthenticatesRefreshToken() throws Exception {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 7200L);
        jwtUtil.initializeSecretKey();

        RedisCacheUtil cache = new RedisCacheUtil();
        ReflectionTestUtils.setField(cache, "redisEnabled", false);
        cache.setJwtVersion(3L, "session-3", 2, TimeUnit.HOURS);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);
        SysUserEntity authorizationState = new SysUserEntity();
        authorizationState.setId(3L);
        authorizationState.setStatus((byte) 1);
        authorizationState.setDelFlag((byte) 0);
        authorizationState.setAuthVersion(0L);
        authorizationState.setGlobalAuthVersion(0L);
        when(userMapper.selectAuthorizationStateById(3L)).thenReturn(authorizationState);
        JwtAuthorizationOnceFilter filter = new JwtAuthorizationOnceFilter(
                jwtUtil, cache, userMapper, permissionMapper, new ObjectMapper().findAndRegisterModules());

        String access = jwtUtil.generateAccessToken(
                Map.of("userId", 3L, "authVersion", 0L, "globalAuthVersion", 0L,
                        "permissions", List.of("system:user:query")), "session-3");
        MockHttpServletRequest accessRequest = new MockHttpServletRequest();
        accessRequest.addHeader("Authorization", "Bearer " + access);
        filter.doFilter(accessRequest, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("system:user:query");

        SecurityContextHolder.clearContext();
        authorizationState.setAuthVersion(1L);
        filter.doFilter(accessRequest, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        SecurityContextHolder.clearContext();
        String refresh = jwtUtil.generateRefreshToken(Map.of("userId", 3L), "session-3");
        MockHttpServletRequest refreshRequest = new MockHttpServletRequest();
        refreshRequest.addHeader("Authorization", "Bearer " + refresh);
        filter.doFilter(refreshRequest, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(permissionMapper);
    }

    @Test
    void returns503WithoutClearingAValidTokenWhenSessionStorageFails() throws Exception {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 7200L);
        jwtUtil.initializeSecretKey();
        RedisCacheUtil failingCache = new RedisCacheUtil() {
            @Override
            public String getJwtVersion(Long userId) {
                throw new IllegalStateException("Redis unavailable");
            }
        };
        JwtAuthorizationOnceFilter filter = new JwtAuthorizationOnceFilter(
                jwtUtil, failingCache, mock(SysUserMapper.class), mock(SysPermissionMapper.class),
                new ObjectMapper().findAndRegisterModules());
        String access = jwtUtil.generateAccessToken(
                Map.of("userId", 3L, "authVersion", 0L, "globalAuthVersion", 0L,
                        "permissions", List.of()), "session-3");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + access);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("服务暂不可用");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void expandsAdminWildcardAfterSessionAndAuthorizationStateValidation() throws Exception {
        JwtUtil jwtUtil = jwtUtil();
        RedisCacheUtil cache = localCache();
        cache.setJwtVersion(1L, "admin-session", 2, TimeUnit.HOURS);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectAuthorizationStateById(1L)).thenReturn(activeUser(1L, 7L, 4L));
        SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);
        when(permissionMapper.selectAllEnabledPermKeys())
                .thenReturn(java.util.Set.of("admin:*", "system:user:query", "role:add"));
        JwtAuthorizationOnceFilter filter = new JwtAuthorizationOnceFilter(
                jwtUtil, cache, userMapper, permissionMapper, new ObjectMapper().findAndRegisterModules());
        String token = jwtUtil.generateAccessToken(
                Map.of("userId", 1L, "authVersion", 7L, "globalAuthVersion", 4L,
                        "permissions", List.of("admin:*")),
                "admin-session");
        MockHttpServletRequest request = bearerRequest(token);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("admin:*", "system:user:query", "role:add");
    }

    @Test
    void invalidAdminSessionNeverQueriesEnabledPermissions() throws Exception {
        JwtUtil jwtUtil = jwtUtil();
        RedisCacheUtil cache = localCache();
        SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        JwtAuthorizationOnceFilter filter = new JwtAuthorizationOnceFilter(
                jwtUtil, cache, userMapper, permissionMapper, new ObjectMapper().findAndRegisterModules());
        String token = jwtUtil.generateAccessToken(
                Map.of("userId", 1L, "authVersion", 0L, "globalAuthVersion", 0L,
                        "permissions", List.of("admin:*")),
                "missing-session");

        filter.doFilter(bearerRequest(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userMapper, permissionMapper);
    }

    @Test
    void staleAdminAuthorizationVersionNeverQueriesEnabledPermissions() throws Exception {
        JwtUtil jwtUtil = jwtUtil();
        RedisCacheUtil cache = localCache();
        cache.setJwtVersion(1L, "stale-admin-session", 2, TimeUnit.HOURS);
        SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectAuthorizationStateById(1L)).thenReturn(activeUser(1L, 2L));
        JwtAuthorizationOnceFilter filter = new JwtAuthorizationOnceFilter(
                jwtUtil, cache, userMapper, permissionMapper, new ObjectMapper().findAndRegisterModules());
        String token = jwtUtil.generateAccessToken(
                Map.of("userId", 1L, "authVersion", 1L, "globalAuthVersion", 0L,
                        "permissions", List.of("admin:*")),
                "stale-admin-session");

        filter.doFilter(bearerRequest(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(permissionMapper);
    }

    @Test
    void legacyTokenWithoutGlobalAuthorizationVersionNeverAuthenticatesOrQueriesPermissions() throws Exception {
        JwtUtil jwtUtil = jwtUtil();
        RedisCacheUtil cache = localCache();
        cache.setJwtVersion(1L, "stale-global-session", 2, TimeUnit.HOURS);
        SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectAuthorizationStateById(1L)).thenReturn(activeUser(1L, 2L, 6L));
        JwtAuthorizationOnceFilter filter = new JwtAuthorizationOnceFilter(
                jwtUtil, cache, userMapper, permissionMapper, new ObjectMapper().findAndRegisterModules());
        String token = jwtUtil.generateAccessToken(
                Map.of("userId", 1L, "authVersion", 2L, "permissions", List.of("admin:*")),
                "stale-global-session");

        filter.doFilter(bearerRequest(token), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(permissionMapper);
    }

    private JwtUtil jwtUtil() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 7200L);
        jwtUtil.initializeSecretKey();
        return jwtUtil;
    }

    private RedisCacheUtil localCache() {
        RedisCacheUtil cache = new RedisCacheUtil();
        ReflectionTestUtils.setField(cache, "redisEnabled", false);
        return cache;
    }

    private SysUserEntity activeUser(Long id, Long authVersion) {
        return activeUser(id, authVersion, 0L);
    }

    private SysUserEntity activeUser(Long id, Long authVersion, Long globalAuthVersion) {
        SysUserEntity user = new SysUserEntity();
        user.setId(id);
        user.setStatus((byte) 1);
        user.setDelFlag((byte) 0);
        user.setAuthVersion(authVersion);
        user.setGlobalAuthVersion(globalAuthVersion);
        return user;
    }

    private MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
