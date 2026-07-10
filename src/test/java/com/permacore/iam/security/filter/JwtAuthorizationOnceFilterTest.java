package com.permacore.iam.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.permacore.iam.utils.JwtUtil;
import com.permacore.iam.utils.RedisCacheUtil;
import com.permacore.iam.mapper.SysUserMapper;
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
        SysUserEntity authorizationState = new SysUserEntity();
        authorizationState.setId(3L);
        authorizationState.setStatus((byte) 1);
        authorizationState.setDelFlag((byte) 0);
        authorizationState.setAuthVersion(0L);
        when(userMapper.selectAuthorizationStateById(3L)).thenReturn(authorizationState);
        JwtAuthorizationOnceFilter filter = new JwtAuthorizationOnceFilter(
                jwtUtil, cache, userMapper, new ObjectMapper().findAndRegisterModules());

        String access = jwtUtil.generateAccessToken(
                Map.of("userId", 3L, "authVersion", 0L,
                        "permissions", List.of("system:user:query")), "session-3");
        MockHttpServletRequest accessRequest = new MockHttpServletRequest();
        accessRequest.addHeader("Authorization", "Bearer " + access);
        filter.doFilter(accessRequest, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();

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
                jwtUtil, failingCache, mock(SysUserMapper.class), new ObjectMapper().findAndRegisterModules());
        String access = jwtUtil.generateAccessToken(
                Map.of("userId", 3L, "authVersion", 0L, "permissions", List.of()), "session-3");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + access);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("服务暂不可用");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
