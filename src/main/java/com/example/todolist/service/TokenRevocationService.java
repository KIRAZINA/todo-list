package com.example.todolist.service;

import com.example.todolist.entity.RevokedToken;
import com.example.todolist.repository.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final RevokedTokenRepository revokedTokenRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String REVOKED_KEY_PREFIX = "revoked:token:";

    @Transactional
    public void revoke(String jti, LocalDateTime expiresAt) {
        if (jti == null) return;
        if (redisTemplate != null) {
            try {
                long ttlSeconds = Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
                if (ttlSeconds > 0) {
                    redisTemplate.opsForValue().set(REVOKED_KEY_PREFIX + jti, "1", ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
                }
            } catch (Exception ignored) {
                // Fall back to DB below.
            }
        }
        revokedTokenRepository.save(RevokedToken.builder()
                .jti(jti)
                .expiresAt(expiresAt)
                .build());
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String jti) {
        if (jti == null) return false;
        if (redisTemplate != null) {
            try {
                Boolean exists = redisTemplate.hasKey(REVOKED_KEY_PREFIX + jti);
                if (Boolean.TRUE.equals(exists)) {
                    return true;
                }
            } catch (Exception ignored) {
                // Fall back to DB below.
            }
        }
        return revokedTokenRepository.existsById(jti);
    }

    @Transactional
    @Scheduled(fixedRate = 3_600_000)
    public void purgeExpired() {
        revokedTokenRepository.deleteExpired(LocalDateTime.now());
    }
}
