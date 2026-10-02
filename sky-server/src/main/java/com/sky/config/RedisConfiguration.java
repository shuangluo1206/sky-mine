package com.sky.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Slf4j
public class RedisConfiguration {
    @Bean
    public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        log.info("开始创建redis模板对象...");
        RedisTemplate redisTemplate = new RedisTemplate();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        // Boot 3 迁移修复：value 也显式用字符串序列化。
        // 旧值是 JDK 序列化格式（Boot 2 时代默认），跨版本读取会 ClassCast，
        // 需要在 Redis 里清掉旧 key 重写（见 SHOP_STATUS）。
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        return redisTemplate;
    }

}

