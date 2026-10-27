package com.example.todolist.service;

import com.example.todolist.entity.RefreshToken;
import com.example.todolist.entity.User;
import com.example.todolist.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken rt = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000L))
                .build();
        return refreshTokenRepository.save(rt);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<RefreshToken> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return java.util.Optional.empty();
        }
        return refreshTokenRepository.findByToken(token);
    }

    public boolean isValid(RefreshToken rt) {
        return rt != null && rt.getExpiresAt() != null && rt.getExpiresAt().isAfter(LocalDateTime.now());
    }

    @Transactional
    public void revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        refreshTokenRepository.deleteByToken(token);
    }

    @Transactional
    public void revokeAllForUser(User user) {
        if (user == null) {
            return;
        }
        refreshTokenRepository.deleteByUser(user);
    }

    @Transactional
    public RefreshToken rotate(RefreshToken old) {
        refreshTokenRepository.delete(old);
        return createRefreshToken(old.getUser());
    }

    @Transactional
    @Scheduled(fixedRate = 3_600_000)
    public void purgeExpired() {
        refreshTokenRepository.deleteExpired(LocalDateTime.now());
    }
}
