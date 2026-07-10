package com.permacore.iam.utils;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    @Test
    void accessAndRefreshTokensHaveExplicitTypesAndSharedSession() {
        JwtUtil jwtUtil = jwtUtil("0123456789abcdef0123456789abcdef");
        String accessToken = jwtUtil.generateAccessToken(Map.of("userId", 7L), "session-1");
        String refreshToken = jwtUtil.generateRefreshToken(Map.of("userId", 7L), "session-1");

        Claims accessClaims = jwtUtil.parseToken(accessToken);
        Claims refreshClaims = jwtUtil.parseToken(refreshToken);
        assertThat(jwtUtil.isAccessToken(accessClaims)).isTrue();
        assertThat(jwtUtil.isRefreshToken(accessClaims)).isFalse();
        assertThat(jwtUtil.isRefreshToken(refreshClaims)).isTrue();
        assertThat(jwtUtil.getSessionId(accessClaims)).isEqualTo("session-1");
        assertThat(jwtUtil.getSessionId(refreshClaims)).isEqualTo("session-1");
        assertThat(accessClaims.getId()).isNotEqualTo(refreshClaims.getId());
    }

    @Test
    void rejectsShortSigningSecret() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "too-short");
        assertThatThrownBy(jwtUtil::initializeSecretKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void rejectsCompactTokenAboveSafeHeaderBudgetAsBusinessError() {
        JwtUtil jwtUtil = jwtUtil("0123456789abcdef0123456789abcdef");

        assertThatThrownBy(() -> jwtUtil.generateAccessToken(
                Map.of("userId", 7L, "oversized", "x".repeat(5000)), "session-1"))
                .isInstanceOf(com.permacore.iam.security.handler.BusinessException.class)
                .hasMessageContaining("JWT内容超过签发上限");
    }

    static JwtUtil jwtUtil(String secret) {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 7200L);
        jwtUtil.initializeSecretKey();
        return jwtUtil;
    }
}
