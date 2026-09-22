package com.hmdp.ai.service;

import com.hmdp.ai.config.AiProperties;
import com.hmdp.ai.exception.AiRequestRejectedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AiRateLimiter {
    private static final String KEY_PREFIX = "ai:rate:";
    private final StringRedisTemplate redis;
    private final AiProperties properties;

    public AiRateLimiter(StringRedisTemplate redis, AiProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public void check(Long userId, String clientIp) {
        if (redis == null) return;
        String minute = String.valueOf(System.currentTimeMillis() / 60000);
        checkKey("user:" + userId + ":" + minute, properties.getRateLimit().getUserPerMinute(), "用户");
        checkKey("ip:" + normalizeIp(clientIp) + ":" + minute, properties.getRateLimit().getIpPerMinute(), "IP");
        checkKey("global:" + minute, properties.getRateLimit().getGlobalPerMinute(), "系统");
    }

    private void checkKey(String suffix, Integer limit, String dimension) {
        if (limit == null || limit <= 0) return;
        String key = KEY_PREFIX + suffix;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) redis.expire(key, Duration.ofMinutes(2));
        if (count != null && count > limit) throw new AiRequestRejectedException(dimension + "请求过于频繁，请稍后再试");
    }

    private String normalizeIp(String clientIp) {
        return clientIp == null || clientIp.trim().isEmpty() ? "unknown" : clientIp.trim();
    }
}
