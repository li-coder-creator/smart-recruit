package com.recruit.smartrecruit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.concurrent.TimeUnit;


@SpringBootTest
public class RedisTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void testRedis() {

        // 写入 Redis，并设置 60 秒过期
        redisTemplate.opsForValue().set(
                "test:name",
                "smart-recruit",
                60,
                TimeUnit.SECONDS
        );

        // 查询
        Object value = redisTemplate.opsForValue().get("test:name");

        System.out.println("Redis查询结果：" + value);

        // 查询剩余过期时间
        Long ttl = redisTemplate.getExpire("test:name");

        System.out.println("剩余过期时间：" + ttl);
    }
}