package com.bogdatech.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;


@Configuration
public class RedisConfig {

    /**
     * 配置redis
     */
    @Bean
    public JedisPool jedisPool() {
        String cacheHostname = System.getenv("REDISCACHEHOSTNAME");
        String cachekey = System.getenv("REDISCACHEKEY");

        int port = 6380;
        // 配置连接池
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(60);       // 最大连接数
        poolConfig.setMaxIdle(10);        // 最大空闲连接
        poolConfig.setMinIdle(2);         // 最小空闲连接
        poolConfig.setMaxWait(Duration.ofMillis(3000));  // 等待最多 3 秒
        poolConfig.setTestOnBorrow(true); // 取连接时测试可用性
        poolConfig.setTestOnReturn(true); // 还连接时测试可用性
        poolConfig.setBlockWhenExhausted(true); // 连接耗尽时是否阻塞
        poolConfig.setJmxEnabled(false);  // 关闭JMX避免MBean冲突

        DefaultJedisClientConfig config = DefaultJedisClientConfig.builder()
                .password(cachekey)
                .ssl(true)
                .build();
        return new JedisPool(poolConfig, new HostAndPort(cacheHostname, port), config);
    }

}
