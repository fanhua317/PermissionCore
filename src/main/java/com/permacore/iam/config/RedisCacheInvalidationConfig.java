package com.permacore.iam.config;

import com.permacore.iam.utils.RedisCacheUtil;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 缓存失效广播监听配置
 */
@Configuration
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisCacheInvalidationConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheInvalidationConfig.class);

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            @NonNull RedisConnectionFactory connectionFactory,
            MessageListenerAdapter cacheInvalidationListenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(Objects.requireNonNull(cacheInvalidationListenerAdapter),
                new ChannelTopic(RedisCacheUtil.CACHE_INVALIDATION_TOPIC));
        return container;
    }

    @Bean
    public MessageListenerAdapter cacheInvalidationListenerAdapter(RedisCacheUtil redisCacheUtil) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(new RedisCacheInvalidationListener(redisCacheUtil),
                "handleMessage");
        StringRedisSerializer serializer = new StringRedisSerializer();
        adapter.setSerializer(serializer);
        adapter.setStringSerializer(serializer);
        return adapter;
    }

    @RequiredArgsConstructor
    static class RedisCacheInvalidationListener {
        private final RedisCacheUtil redisCacheUtil;

        public void handleMessage(String message) {
            if (message == null || message.isBlank()) {
                return;
            }
            if (RedisCacheUtil.INVALIDATE_ALL.equals(message)) {
                redisCacheUtil.clearLocalCache();
                log.info("接收到全量缓存失效广播");
                return;
            }
            redisCacheUtil.invalidateLocalCacheKey(message);
            log.info("接收到缓存失效广播: key={}", message);
        }
    }
}
