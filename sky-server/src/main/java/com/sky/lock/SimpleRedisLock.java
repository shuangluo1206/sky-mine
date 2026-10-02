package com.sky.lock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 简易Redis分布式锁（防超卖改造③，黑马点评同款思路）
 *
 * 加锁：setIfAbsent = SET key value NX EX ttl，原子操作
 * TTL兜底：持有者宕机时锁自动过期，避免死锁
 *
 * 已知简化（面试可讲）：
 * 1. 释放锁未校验持有者身份（误删他人锁风险）→ 生产用 Lua 比对 value 或直接上 Redisson
 * 2. 无看门狗续期 → TTL要大于业务最大执行时间
 * 3. 拿锁失败直接拒绝 → 可改为自旋等待/消息削峰
 */
@Component
public class SimpleRedisLock {

    private static final String KEY_PREFIX = "lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 尝试获取锁
     * @param name 锁名（业务粒度，如 order:submit）
     * @return true=抢到锁
     */
    public boolean tryLock(String name) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, "1", LOCK_TTL));
    }

    /**
     * 带自旋重试的获取锁：拿不到先排队等，而不是立刻拒绝
     * @param name 锁名
     * @param maxRetry 最大重试次数
     * @param retryIntervalMs 每次重试间隔（毫秒）
     * @return true=在重试次数内抢到锁
     */
    public boolean tryLock(String name, int maxRetry, long retryIntervalMs) throws InterruptedException {
        for (int i = 0; i < maxRetry; i++) {
            if (tryLock(name)) {
                return true;
            }
            Thread.sleep(retryIntervalMs);
        }
        return false;
    }

    /**
     * 释放锁
     * @param name 锁名
     */
    public void unlock(String name) {
        redisTemplate.delete(KEY_PREFIX + name);
    }
}
