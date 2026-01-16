package com.permacore.iam.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 二级缓存工具类
 * L1: Caffeine本地缓存（5分钟）
 * L2: Redis分布式缓存（30分钟） - 可选，不存在时只用本地缓存
 */
@Component
public class RedisCacheUtil {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheUtil.class);

    public static final String CACHE_INVALIDATION_TOPIC = "cache:invalidation";
    public static final String INVALIDATE_ALL = "ALL";

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    // 本地缓存配置
    private final Cache<String, Object> localCache = Caffeine.newBuilder()
            .initialCapacity(100)
            .maximumSize(10_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    // 缓存前缀
    private static final String PERM_USER_KEY = "permission:user:";
    private static final String ROLE_TREE_KEY = "role:children:";
    private static final String JWT_VERSION_KEY = "jwt:version:user:";

    /**
     * 检查 Redis 是否可用
     */
    private boolean isRedisAvailable() {
        return redisTemplate != null;
    }

    /**
     * 检查 Redisson 是否可用
     */
    private boolean isRedissonAvailable() {
        return redissonClient != null;
    }

    /**
     * 获取用户权限缓存（二级缓存）
     */
    @SuppressWarnings("unchecked")
    public Set<String> getUserPermissions(Long userId) {
        String key = PERM_USER_KEY + userId;

        // 1. 先查本地缓存
        Object localValue = localCache.getIfPresent(key);
        if (localValue != null) {
            log.debug("从本地缓存获取权限: userId={}", userId);
            return (Set<String>) localValue;
        }

        // 2. 查Redis（如果可用）
        if (isRedisAvailable()) {
            Set<String> permissions = (Set<String>) redisTemplate.opsForValue().get(key);
            if (permissions != null) {
                log.debug("从Redis获取权限: userId={}", userId);
                // 回填本地缓存
                localCache.put(key, permissions);
                return permissions;
            }
        }

        return null;
    }

    /**
     * 设置用户权限缓存
     */
    public void setUserPermissions(Long userId, Set<String> permissions, long timeout, TimeUnit unit) {
        String key = PERM_USER_KEY + userId;

        // 1. 写Redis（如果可用）
        if (isRedisAvailable()) {
            redisTemplate.opsForValue().set(key, permissions, timeout, unit);
        }
        // 2. 写本地缓存
        localCache.put(key, permissions);

        log.debug("设置用户权限缓存: userId={}", userId);
    }

    /**
     * 删除用户权限缓存（用于权限变更时）
     */
    public void deleteUserPermissions(Long userId) {
        String key = PERM_USER_KEY + userId;

        // 1. 删除本地缓存
        localCache.invalidate(key);
        // 2. 删除Redis（如果可用）
        if (isRedisAvailable()) {
            redisTemplate.delete(key);
        }

        // 广播清理通知（其他节点清理L1）
        publishInvalidation(key);

        log.info("删除用户权限缓存: userId={}", userId);
    }

    /**
     * 获取角色继承树（带分布式锁，防止缓存击穿）
     */
    @SuppressWarnings("unchecked")
    public List<Long> getRoleChildrenWithLock(Long roleId) {
        String key = ROLE_TREE_KEY + roleId;

        // 1. 查本地缓存
        Object localValue = localCache.getIfPresent(key);
        if (localValue != null) {
            return (List<Long>) localValue;
        }

        // 如果 Redisson 不可用，直接返回 null
        if (!isRedissonAvailable()) {
            log.debug("Redisson不可用，跳过分布式锁逻辑");
            return null;
        }

        String lockKey = key + ":lock";
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 2. 尝试获取锁（等待3秒，持有10秒自动释放）
            if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                try {
                    // 3. 二次检查（防止锁竞争）
                    Object localValue2 = localCache.getIfPresent(key);
                    if (localValue2 != null) {
                        return (List<Long>) localValue2;
                    }

                    // 4. 查Redis
                    if (isRedisAvailable()) {
                        List<Long> children = (List<Long>) redisTemplate.opsForValue().get(key);
                        if (children != null) {
                            localCache.put(key, children);
                            return children;
                        }
                    }

                    log.warn("缓存穿透，roleId={}", roleId);
                    return null;
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁失败", e);
        }

        return null;
    }

    /**
     * 获取JWT版本号（用于强制失效）
     */
    public String getJwtVersion(Long userId) {
        String key = JWT_VERSION_KEY + userId;

        // 优先从本地缓存获取
        Object localVersion = localCache.getIfPresent(key);
        if (localVersion != null) {
            return localVersion.toString();
        }

        // 从 Redis 获取（如果可用）
        if (isRedisAvailable()) {
            Object version = redisTemplate.opsForValue().get(key);
            if (version != null) {
                localCache.put(key, version);
                return version.toString();
            }
        }
        return null;
    }

    /**
     * 设置JWT版本号
     */
    public void setJwtVersion(Long userId, String version) {
        String key = JWT_VERSION_KEY + userId;

        // 写本地缓存
        localCache.put(key, version);

        // 写 Redis（如果可用）
        if (isRedisAvailable()) {
            redisTemplate.opsForValue().set(key, version);
        }
    }

    /**
     * 设置JWT版本号（带过期时间）
     */
    public void setJwtVersion(Long userId, String version, long timeout, TimeUnit unit) {
        String key = JWT_VERSION_KEY + userId;

        // 写本地缓存
        localCache.put(key, version);

        // 写 Redis（如果可用）
        if (isRedisAvailable()) {
            redisTemplate.opsForValue().set(key, version, timeout, unit);
        }
    }

    /**
     * 删除JWT版本号
     */
    public void deleteJwtVersion(Long userId) {
        String key = JWT_VERSION_KEY + userId;

        // 删除本地缓存
        localCache.invalidate(key);

        // 删除 Redis（如果可用）
        if (isRedisAvailable()) {
            redisTemplate.delete(key);
        }

        // 广播清理通知（其他节点清理L1）
        publishInvalidation(key);
    }

    /**
     * 清除本地缓存
     */
    public void clearLocalCache() {
        localCache.invalidateAll();
        log.info("本地缓存已清除");
    }

    /**
     * 仅清除本地缓存中的指定 key（用于接收广播）
     */
    public void invalidateLocalCacheKey(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        localCache.invalidate(key);
        log.debug("本地缓存已失效: key={}", key);
    }

    /**
     * 广播缓存失效通知（分布式环境）
     */
    private void publishInvalidation(String key) {
        if (!isRedisAvailable()) {
            return;
        }
        redisTemplate.convertAndSend(CACHE_INVALIDATION_TOPIC, key);
    }
}