package com.permacore.iam.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;
import org.redisson.config.SingleServerConfig;

/**
 * Redis配置类
 * 仅在配置 app.redis.enabled=true 时启用
 */
@Configuration
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisConfig {

    /**
     * Redisson客户端（推荐，支持分布式锁）
     */
    @Bean
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        String redisUrl = String.format("redis://%s:%s",
                redisProperties.getHost(),
                redisProperties.getPort());

        SingleServerConfig serverConfig = config.useSingleServer();
        serverConfig.setAddress(redisUrl);

        if (StringUtils.hasText(redisProperties.getPassword())) {
            serverConfig.setPassword(redisProperties.getPassword());
        }

        // RedisProperties#getDatabase 返回 int（默认0），直接设置即可
        serverConfig.setDatabase(redisProperties.getDatabase());

        return Redisson.create(config);
    }

    /**
     * RedisTemplate配置
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key序列化
        template.setKeySerializer(new StringRedisSerializer());
        // Value序列化
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        // Hash Key序列化
        template.setHashKeySerializer(new StringRedisSerializer());
        // Hash Value序列化
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }
}