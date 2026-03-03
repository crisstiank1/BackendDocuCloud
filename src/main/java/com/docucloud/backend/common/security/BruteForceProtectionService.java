package com.docucloud.backend.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class BruteForceProtectionService {

    private static final Logger log = LoggerFactory.getLogger(BruteForceProtectionService.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;

    private final StringRedisTemplate redisTemplate;

    public BruteForceProtectionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isLocked(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey("lock:" + key));
        } catch (Exception e) {
            log.warn("[BruteForce] Redis no disponible en isLocked({}), permitiendo acceso: {}", key, e.getMessage());
            return false; // fail-open: si Redis falla, no bloqueamos al usuario
        }
    }

    public void onFail(String key) {
        try {
            String attemptsKey = "attempts:" + key;
            Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
            redisTemplate.expire(attemptsKey, Duration.ofMinutes(LOCK_MINUTES));

            if (attempts != null && attempts >= MAX_ATTEMPTS) {
                redisTemplate.opsForValue().set("lock:" + key, "1", Duration.ofMinutes(LOCK_MINUTES));
                redisTemplate.delete(attemptsKey);
                log.warn("[BruteForce] Cuenta bloqueada: {}", key);
            }
        } catch (Exception e) {
            log.warn("[BruteForce] Redis no disponible en onFail({}): {}", key, e.getMessage());
        }
    }

    public void onSuccess(String key) {
        try {
            redisTemplate.delete("attempts:" + key);
            redisTemplate.delete("lock:" + key);
        } catch (Exception e) {
            log.warn("[BruteForce] Redis no disponible en onSuccess({}): {}", key, e.getMessage());
        }
    }
}
