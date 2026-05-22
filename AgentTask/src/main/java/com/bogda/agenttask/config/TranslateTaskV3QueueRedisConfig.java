package com.bogda.agenttask.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 队列专用 {@link StringRedisTemplate}，与监控用 {@code RedisTemplate} 的 JSON 序列化分离，保证 LPUSH/BRPOP 为纯字符串。
 */
@Configuration
public class TranslateTaskV3QueueRedisConfig {

    public static final String QUEUE_STRING_REDIS_TEMPLATE = "translateTaskV3QueueStringRedisTemplate";

    @Bean(name = QUEUE_STRING_REDIS_TEMPLATE)
    public StringRedisTemplate translateTaskV3QueueStringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory);
        template.afterPropertiesSet();
        return template;
    }
}
