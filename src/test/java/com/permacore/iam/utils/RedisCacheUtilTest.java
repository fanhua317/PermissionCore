package com.permacore.iam.utils;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisCacheUtilTest {

    @Test
    void localSessionVersionUsesProvidedTtlAndRotatesOnlyOnce() {
        RedisCacheUtil cache = new RedisCacheUtil();
        ReflectionTestUtils.setField(cache, "redisEnabled", false);

        cache.setJwtVersion(9L, "old-session", 1, TimeUnit.HOURS);
        assertThat(cache.getJwtVersion(9L)).isEqualTo("old-session");
        assertThat(cache.rotateJwtVersion(9L, "old-session", "new-session", 2, TimeUnit.HOURS)).isTrue();
        assertThat(cache.rotateJwtVersion(9L, "old-session", "replay", 2, TimeUnit.HOURS)).isFalse();
        assertThat(cache.getJwtVersion(9L)).isEqualTo("new-session");
    }

    @Test
    void logoutCannotBeUndoneByAnOldRefreshRotation() {
        RedisCacheUtil cache = new RedisCacheUtil();
        ReflectionTestUtils.setField(cache, "redisEnabled", false);

        cache.setJwtVersion(11L, "old-session", 1, TimeUnit.HOURS);
        cache.deleteJwtVersion(11L);

        assertThat(cache.rotateJwtVersion(11L, "old-session", "revived", 1, TimeUnit.HOURS)).isFalse();
        assertThat(cache.getJwtVersion(11L)).isNull();
    }

    @Test
    void enabledRedisNeverSilentlyFallsBackToProcessLocalState() {
        RedisCacheUtil cache = new RedisCacheUtil();
        ReflectionTestUtils.setField(cache, "redisEnabled", true);

        assertThatThrownBy(() -> cache.getJwtVersion(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("StringRedisTemplate");
    }
}
