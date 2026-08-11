package com.example.todo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory fixed-window rate limiter keyed by client IP.
 * Tracks the timestamps of recent requests per key and denies requests
 * that exceed the configured budget within the configured window.
 * State is per-instance and in-memory; good enough for this local test
 * tool (a single backend instance). For a multi-instance deployment this
 * would need to move to a shared store (e.g. Redis).
 */
@Service
public class RateLimitService {

    private final boolean enabled;
    private final int maxRequests;
    private final long windowMs;
    private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public RateLimitService(
            @Value("${security.rate-limit.enabled:true}") boolean enabled,
            @Value("${security.rate-limit.max-requests:10}") int maxRequests,
            @Value("${security.rate-limit.window-ms:60000}") long windowMs) {
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    /**
     * Records a request for the given key and reports whether it is
     * within the configured budget.
     */
    public boolean isAllowed(String key) {
        if (!enabled) return true;

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
