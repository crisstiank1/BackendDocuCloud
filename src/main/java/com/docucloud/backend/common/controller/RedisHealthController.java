package com.docucloud.backend.common.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
public class RedisHealthController {

    private final StringRedisTemplate redis;

    public RedisHealthController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @GetMapping("/api/dev/redis/ping")
    public String ping() {
        redis.opsForValue().set("docucloud:ping", "ok");
        return redis.opsForValue().get("docucloud:ping");
    }
}
