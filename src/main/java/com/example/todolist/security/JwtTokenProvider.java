package com.example.todolist.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final int MIN_SECRET_LENGTH_BYTES = 32;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    @PostConstruct
    public void validateSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalArgumentException(
                "JWT secret must not be empty. " +
                "Please set JWT_SECRET environment variable or jwt.secret property " +
                "to a valid Base64-encoded secret key (at least 256 bits / 32 bytes)."
            );
        }
        byte[] secretBytes;
        try {
            secretBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                "JWT secret must be a valid Base64-encoded string. " +
                "Please set JWT_SECRET to a Base64-encoded value of at least 256 bits (32 bytes).",
                ex
            );
        }
        if (secretBytes.length < MIN_SECRET_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                String.format(
                    "JWT secret must decode to at least %d bytes (256 bits) for HS256 security. " +
                    "Current decoded length: %d bytes. " +
                    "Please set JWT_SECRET to a Base64-encoded value of at least 256 bits.",
                    MIN_SECRET_LENGTH_BYTES, secretBytes.length
                )
            );
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        String role = userDetails.getAuthorities().iterator().next().getAuthority().replaceFirst("ROLE_", "");

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .id(UUID.randomUUID().toString())
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsParser()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public String getJtiFromToken(String token) {
        Claims claims = getClaimsParser()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("jti", String.class);
    }

    public Date getExpirationFromToken(String token) {
        Claims claims = getClaimsParser()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getExpiration();
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            getClaimsParser().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private JwtParser getClaimsParser() {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build();
    }
}
