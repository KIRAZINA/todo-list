package com.example.todo.service;

import com.example.todo.entity.RevokedToken;
import com.example.todo.repository.RevokedTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    @Mock
    private RevokedTokenRepository revokedTokenRepository;

    @InjectMocks
    private TokenRevocationService tokenRevocationService;

    @Test
    void shouldPersistRevocation() {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        tokenRevocationService.revoke("jti-123", expiresAt);

        verify(revokedTokenRepository).save(argThat(t ->
                t.getJti().equals("jti-123") && t.getExpiresAt().equals(expiresAt)));
    }

    @Test
    void shouldIgnoreNullJti() {
        tokenRevocationService.revoke(null, LocalDateTime.now().plusHours(1));

        verify(revokedTokenRepository, never()).save(any(RevokedToken.class));
    }

    @Test
    void shouldReportRevokedStatus() {
        when(revokedTokenRepository.existsById("jti-123")).thenReturn(true);
        when(revokedTokenRepository.existsById("jti-456")).thenReturn(false);

        assertTrue(tokenRevocationService.isRevoked("jti-123"));
        assertFalse(tokenRevocationService.isRevoked("jti-456"));
        assertFalse(tokenRevocationService.isRevoked(null));
    }

    @Test
    void shouldPurgeExpiredEntries() {
        tokenRevocationService.purgeExpired();

        verify(revokedTokenRepository).deleteExpired(any(LocalDateTime.class));
    }
}
