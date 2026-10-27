package com.example.todolist.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * IP-based rate limiter. Uses Redis when available for distributed state across
 * instances; falls back to an in-memory fixed-window counter otherwise.
 */
@Service
public class RateLimitService {

    private final boolean enabled;
    private final int maxRequests;
    private final long windowMs;
    private final StringRedisTemplate redisTemplate;

    private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public RateLimitService(boolean enabled, int maxRequests, long windowMs) {
        this(enabled, maxRequests, windowMs, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RateLimitService(
            @org.springframework.beans.factory.annotation.Value("${security.rate-limit.enabled:true}") boolean enabled,
            @org.springframework.beans.factory.annotation.Value("${security.rate-limit.max-requests:10}") int maxRequests,
            @org.springframework.beans.factory.annotation.Value("${security.rate-limit.window-ms:60000}") long windowMs,
            StringRedisTemplate redisTemplate) {
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String key) {
        if (!enabled) return true;

        if (redisTemplate != null) {
            try {
                String redisKey = "rate_limit:" + key;
                Long count = redisTemplate.opsForValue().increment(redisKey);
                if (count != null) {
                    if (count == 1) {
                        redisTemplate.expire(redisKey, windowMs, TimeUnit.MILLISECONDS);
                    }
                    return count <= maxRequests;
                }
            } catch (Exception ignored) {
                // Fall back to in-memory on Redis failure.
            }
        }

        return inMemoryAllowed(key);
    }

    private boolean inMemoryAllowed(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            long cutoff = now - windowMs;
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= maxRequests) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
