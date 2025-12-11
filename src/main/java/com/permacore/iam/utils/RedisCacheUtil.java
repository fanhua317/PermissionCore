package com.permacore.iam.utils;

import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
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
 * L2: Redis分布式缓存（30分钟）
 */
@Slf4j
@Component
public class RedisCacheUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
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
     * 获取用户权限缓存（二级缓存）
     */
    public Set<String> getUserPermissions(Long userId) {
        String key = PERM_USER_KEY + userId;

        // 1. 先查本地缓存
        Object localValue = localCache.getIfPresent(key);
        if (localValue != null) {
            log.debug("从本地缓存获取权限: userId={}", userId);
            return (Set<String>) localValue;
        }

        // 2. 查Redis
        Set<String> permissions = (Set<String>) redisTemplate.opsForValue().get(key);
        if (permissions != null) {
            log.debug("从Redis获取权限: userId={}", userId);
            // 回填本地缓存
            localCache.put(key, permissions);
            return permissions;
        }

        return null;
    }

    /**
     * 设置用户权限缓存
     */
    public void setUserPermissions(Long userId, Set<String> permissions, long timeout, TimeUnit unit) {
        String key = PERM_USER_KEY + userId;

        // 1. 写Redis
        redisTemplate.opsForValue().set(key, permissions, timeout, unit);
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
        // 2. 删除Redis
        redisTemplate.delete(key);

        log.info("删除用户权限缓存: userId={}", userId);
    }

    /**
     * 获取角色继承树（带分布式锁，防止缓存击穿）
     */
    public List<Long> getRoleChildrenWithLock(Long roleId) {
        String key = ROLE_TREE_KEY + roleId;
        String lockKey = key + ":lock";

        // 1. 查本地缓存
        Object localValue = localCache.getIfPresent(key);
        if (localValue != null) {
            return (List<Long>) localValue;
        }

        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 2. 尝试获取锁（等待3秒，持有10秒自动释放）
            if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                // 3. 二次检查（防止锁竞争）
                Object localValue2 = localCache.getIfPresent(key);
                if (localValue2 != null) {
                    return (List<Long>) localValue2;
                }

                // 4. 查Redis
                List<Long> children = (List<Long>) redisTemplate.opsForValue().get(key);
                if (children != null) {
                    localCache.put(key, children);
                    return children;
                }

                log.warn("缓存穿透，roleId={}", roleId);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁失败", e);
        } finally {
            lock.unlock();
        }

        return null;
    }

    /**
     * 获取JWT版本号（用于强制失效）
     */
    public String getJwtVersion(Long userId) {
        String key = JWT_VERSION_KEY + userId;
        Object version = redisTemplate.opsForValue().get(key);
        return version != null ? version.toString() : null;
    }

    /**
     * 设置JWT版本号
     */
    public void setJwtVersion(Long userId, String version, long timeout, TimeUnit unit) {
        String key = JWT_VERSION_KEY + userId;
        redisTemplate.opsForValue().set(key, version, timeout, unit);
    }

    /**
     * 删除JWT版本号（强制登出）
     */
    public void deleteJwtVersion(Long userId) {
        String key = JWT_VERSION_KEY + userId;
        redisTemplate.delete(key);
    }

    /**
     * 清理所有缓存（仅用于测试）
     */
    public void clearAll() {
        localCache.invalidateAll();
        redisTemplate.getConnectionFactory().getConnection().flushDb();
    }
}