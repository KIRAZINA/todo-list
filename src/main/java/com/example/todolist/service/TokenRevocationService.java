package com.example.todolist.service;

import com.example.todolist.entity.RevokedToken;
import com.example.todolist.repository.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final RevokedTokenRepository revokedTokenRepository;

    @Transactional
    public void revoke(String jti, LocalDateTime expiresAt) {
        if (jti == null) return; // token had no jti claim вЂ” nothing to revoke by
        revokedTokenRepository.save(RevokedToken.builder()
                .jti(jti)
                .expiresAt(expiresAt)
                .build());
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String jti) {
        if (jti == null) return false;
        return revokedTokenRepository.existsById(jti);
    }

    // Runs hourly; purges revocation entries whose underlying token has
    // already expired naturally, since keeping them serves no purpose.
    @Transactional
    @Scheduled(fixedRate = 3_600_000)
    public void purgeExpired() {
        revokedTokenRepository.deleteExpired(LocalDateTime.now());
    }
}
