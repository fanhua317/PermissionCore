package com.permacore.iam.controller;

import com.permacore.iam.domain.vo.Result;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 无敏感信息的存活/依赖健康探针，供容器编排使用。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final boolean redisEnabled;

    public HealthController(JdbcTemplate jdbcTemplate,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${app.redis.enabled:false}") boolean redisEnabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplateProvider = redisTemplateProvider;
        this.redisEnabled = redisEnabled;
    }

    @GetMapping
    public Result<Void> health() {
        Integer databaseProbe = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        if (!Integer.valueOf(1).equals(databaseProbe)) {
            throw new IllegalStateException("Database health check failed");
        }

        if (redisEnabled) {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate == null || redisTemplate.getConnectionFactory() == null) {
                throw new IllegalStateException("Redis health check is unavailable");
            }
            try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
                if (!"PONG".equalsIgnoreCase(connection.ping())) {
                    throw new IllegalStateException("Redis health check failed");
                }
            }
        }

        return Result.success();
    }
}
