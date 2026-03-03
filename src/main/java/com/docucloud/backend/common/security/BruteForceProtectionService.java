package com.docucloud.backend.common.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class BruteForceProtectionService {

    private final StringRedisTemplate redis;

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    public BruteForceProtectionService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isLocked(String key) {
        return Boolean.TRUE.equals(redis.hasKey(lockKey(key)));
    }

    public void onFail(String key) {
        Long attempts = redis.opsForValue().increment(attemptsKey(key));
        redis.expire(attemptsKey(key), WINDOW);

        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            redis.opsForValue().set(lockKey(key), "1", WINDOW);
        }
    }

    public void onSuccess(String key) {
        redis.delete(attemptsKey(key));
        redis.delete(lockKey(key));
    }

    private String attemptsKey(String key) { return "auth:attempts:" + key; }
    private String lockKey(String key) { return "auth:lock:" + key; }
}
