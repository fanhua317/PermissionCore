package com.permacore.iam.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 会话版本存储。启用 Redis 时始终以 Redis 为唯一事实源；
 * 未启用 Redis 的单机开发环境使用带独立过期时间的本地缓存。
 */
@Component
public class RedisCacheUtil {

    private static final String JWT_VERSION_KEY = "jwt:version:user:";

    @Value("${app.redis.enabled:false}")
    private boolean redisEnabled;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> ROTATE_SESSION_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
                return 1
            end
            return 0
            """, Long.class);

    private final Cache<String, ExpiringValue> localSessionVersions = Caffeine.newBuilder()
            .initialCapacity(100)
            .maximumSize(10_000)
            .build();

    @PostConstruct
    void validateConfiguration() {
        if (redisEnabled && stringRedisTemplate == null) {
            throw new IllegalStateException("app.redis.enabled=true requires StringRedisTemplate");
        }
    }

    public String getJwtVersion(Long userId) {
        String key = JWT_VERSION_KEY + userId;
        if (redisEnabled) {
            return redisTemplate().opsForValue().get(key);
        }

        ExpiringValue localValue = localSessionVersions.getIfPresent(key);
        if (localValue != null) {
            if (!localValue.isExpired()) {
                return localValue.value();
            }
            localSessionVersions.invalidate(key);
        }

        return null;
    }

    public void setJwtVersion(Long userId, @NonNull String version, long timeout, @NonNull TimeUnit unit) {
        String key = JWT_VERSION_KEY + userId;
        long ttlMillis = unit.toMillis(timeout);
        if (ttlMillis <= 0) {
            deleteJwtVersion(userId);
            return;
        }

        if (redisEnabled) {
            redisTemplate().opsForValue().set(key, version, timeout, unit);
            localSessionVersions.invalidate(key);
            return;
        }
        synchronized (localSessionVersions) {
            localSessionVersions.put(key, new ExpiringValue(version, System.currentTimeMillis() + ttlMillis));
        }
    }

    /**
     * 原子轮换会话版本。Refresh Token 重放或并发使用时，最多一个请求能够成功。
     */
    public boolean rotateJwtVersion(Long userId, @NonNull String expectedVersion, @NonNull String newVersion,
            long timeout, @NonNull TimeUnit unit) {
        String key = JWT_VERSION_KEY + userId;
        long ttlSeconds = unit.toSeconds(timeout);
        if (ttlSeconds <= 0) {
            return false;
        }

        boolean rotated;
        if (redisEnabled) {
            Long result = redisTemplate().execute(
                    ROTATE_SESSION_SCRIPT,
                    Collections.singletonList(key),
                    expectedVersion,
                    newVersion,
                    Long.toString(ttlSeconds));
            rotated = Long.valueOf(1L).equals(result);
        } else {
            synchronized (localSessionVersions) {
                ExpiringValue current = localSessionVersions.getIfPresent(key);
                rotated = current != null && !current.isExpired() && expectedVersion.equals(current.value());
                if (rotated) {
                    localSessionVersions.put(key,
                            new ExpiringValue(newVersion, System.currentTimeMillis() + unit.toMillis(timeout)));
                }
            }
        }

        if (rotated && redisEnabled) {
            localSessionVersions.invalidate(key);
        }
        return rotated;
    }

    public void deleteJwtVersion(Long userId) {
        String key = JWT_VERSION_KEY + userId;
        if (redisEnabled) {
            localSessionVersions.invalidate(key);
            redisTemplate().delete(key);
            return;
        }
        synchronized (localSessionVersions) {
            localSessionVersions.invalidate(key);
        }
    }

    private StringRedisTemplate redisTemplate() {
        if (stringRedisTemplate == null) {
            throw new IllegalStateException("Redis is enabled but StringRedisTemplate is unavailable");
        }
        return stringRedisTemplate;
    }

    private record ExpiringValue(String value, long expiresAtMillis) {
        private boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMillis;
        }
    }
}
