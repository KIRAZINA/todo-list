package com.example.todolist.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class JwtTokenProviderTest {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    void shouldGenerateAndValidateToken() {
        com.example.todolist.entity.User user = com.example.todolist.entity.User.builder()
                .username("testuser")
                .password("password")
                .email("test@example.com")
                .role("USER")
                .build();
        
        UserDetails userDetails = new CustomUserDetails(user);
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                userDetails, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        String token = jwtTokenProvider.generateToken(auth);

        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("testuser", jwtTokenProvider.getUsernameFromToken(token));
    }

    @Test
    void shouldExtractUsernameFromToken() {
        com.example.todolist.entity.User user = com.example.todolist.entity.User.builder()
                .username("john.doe")
                .password("password")
                .email("john@example.com")
                .role("USER")
                .build();
        
        UserDetails userDetails = new CustomUserDetails(user);
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                userDetails, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        String token = jwtTokenProvider.generateToken(auth);
        String username = jwtTokenProvider.getUsernameFromToken(token);

        assertEquals("john.doe", username);
    }

    @Test
    void shouldRejectExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        String expiredToken = Jwts.builder()
                .subject("testuser")
                .issuedAt(new Date(System.currentTimeMillis() - 10000))
                .expiration(new Date(System.currentTimeMillis() - 5000))
                .signWith(key)
                .compact();

        assertFalse(jwtTokenProvider.validateToken(expiredToken));
    }

    @Test
    void shouldRejectMalformedToken() {
        String malformedToken = "this.is.not.a.valid.jwt";

        assertFalse(jwtTokenProvider.validateToken(malformedToken));
    }

    @Test
    void shouldRejectTokenWithInvalidSignature() {
        SecretKey wrongKey = Keys.hmacShaKeyFor("different-secret-key-at-least-256-bits-1234567890abcdef".getBytes());
        String tokenWithWrongSignature = Jwts.builder()
                .subject("testuser")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(wrongKey)
                .compact();

        assertFalse(jwtTokenProvider.validateToken(tokenWithWrongSignature));
    }

    @Test
    void shouldRejectEmptyToken() {
        assertFalse(jwtTokenProvider.validateToken(""));
    }

    @Test
    void shouldRejectNullToken() {
        assertFalse(jwtTokenProvider.validateToken(null));
    }

    @Test
    void shouldGenerateTokenWithCorrectClaims() {
        com.example.todolist.entity.User user = com.example.todolist.entity.User.builder()
                .username("testuser")
                .password("password")
                .email("test@example.com")
                .role("USER")
                .build();
        
        UserDetails userDetails = new CustomUserDetails(user);
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                userDetails, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        String token = jwtTokenProvider.generateToken(auth);

        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3);
    }

    @Test
    void shouldGenerateTokenWithJtiAndExpiration() {
        com.example.todolist.entity.User user = com.example.todolist.entity.User.builder()
                .username("jti_user")
                .password("password")
                .email("jti@example.com")
                .role("USER")
                .build();

        UserDetails userDetails = new CustomUserDetails(user);
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                userDetails, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        String token = jwtTokenProvider.generateToken(auth);

        assertNotNull(jwtTokenProvider.getJtiFromToken(token), "JWT should carry a jti claim for revocation");
        assertNotNull(jwtTokenProvider.getExpirationFromToken(token));
        assertTrue(jwtTokenProvider.getExpirationFromToken(token).after(new Date()));
    }

    @Test
    void shouldHandleAdminRole() {
        com.example.todolist.entity.User user = com.example.todolist.entity.User.builder()
                .username("admin")
                .password("password")
                .email("admin@example.com")
                .role("ADMIN")
                .build();
        
        UserDetails userDetails = new CustomUserDetails(user);
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                userDetails, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

        String token = jwtTokenProvider.generateToken(auth);

        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("admin", jwtTokenProvider.getUsernameFromToken(token));
    }

    @Test
    void shouldGenerateDifferentTokensForDifferentUsers() {
        com.example.todolist.entity.User user1 = com.example.todolist.entity.User.builder()
                .username("user1")
                .password("password")
                .email("user1@example.com")
                .role("USER")
                .build();
        
        com.example.todolist.entity.User user2 = com.example.todolist.entity.User.builder()
                .username("user2")
                .password("password")
                .email("user2@example.com")
                .role("USER")
                .build();
        
        UserDetails ud1 = new CustomUserDetails(user1);
        UserDetails ud2 = new CustomUserDetails(user2);
        Authentication auth1 = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                ud1, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication auth2 = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                ud2, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        String token1 = jwtTokenProvider.generateToken(auth1);
        String token2 = jwtTokenProvider.generateToken(auth2);

        assertNotEquals(token1, token2);
        assertEquals("user1", jwtTokenProvider.getUsernameFromToken(token1));
        assertEquals("user2", jwtTokenProvider.getUsernameFromToken(token2));
    }
}
