package com.example.todolist.controller;

import com.example.todolist.dto.user.UserLoginRequest;
import com.example.todolist.dto.user.UserRegisterRequest;
import com.example.todolist.dto.user.UserResponse;
import com.example.todolist.entity.RefreshToken;
import com.example.todolist.entity.User;
import com.example.todolist.security.CustomUserDetails;
import com.example.todolist.security.JwtTokenProvider;
import com.example.todolist.service.RefreshTokenService;
import com.example.todolist.service.TokenRevocationService;
import com.example.todolist.service.UserService;
import com.example.todolist.exception.UserNotAuthenticatedException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenRevocationService tokenRevocationService;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody UserRegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody UserLoginRequest request, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Successful login clears any lockout state.
            userService.resetFailedLogin(request.getUsername());

            String jwt = jwtTokenProvider.generateToken(authentication);

            User user = ((CustomUserDetails) authentication.getPrincipal()).getUser();
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
            addRefreshTokenCookie(response, refreshToken.getToken());

            return Map.of("token", jwt, "type", "Bearer");
        } catch (BadCredentialsException ex) {
            userService.recordFailedLogin(request.getUsername());
            throw ex;
        }
    }

    @PostMapping("/refresh")
    public Map<String, String> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshTokenValue = extractRefreshTokenCookie(request);
        RefreshToken refreshToken = refreshTokenService.findByToken(refreshTokenValue)
                .orElseThrow(() -> new UserNotAuthenticatedException("Invalid refresh token"));

        if (!refreshTokenService.isValid(refreshToken)) {
            refreshTokenService.revokeToken(refreshTokenValue);
            throw new UserNotAuthenticatedException("Invalid refresh token");
        }

        // Rotate refresh token
        RefreshToken newRefreshToken = refreshTokenService.rotate(refreshToken);
        addRefreshTokenCookie(response, newRefreshToken.getToken());

        // Issue new short-lived access token
        User user = refreshToken.getUser();
        // Build authentication for token generation
        org.springframework.security.core.userdetails.UserDetails userDetails =
                new com.example.todolist.security.CustomUserDetails(user);
        Authentication authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        String jwt = jwtTokenProvider.generateToken(authentication);
        return Map.of("token", jwt, "type", "Bearer");
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                       HttpServletRequest request, HttpServletResponse response) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String jwt = authorizationHeader.substring(7);
            String jti = jwtTokenProvider.getJtiFromToken(jwt);
            LocalDateTime expiresAt = jwtTokenProvider.getExpirationFromToken(jwt)
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
            tokenRevocationService.revoke(jti, expiresAt);
        }

        String refreshTokenValue = extractRefreshTokenCookie(request);
        refreshTokenService.revokeToken(refreshTokenValue);
        clearRefreshTokenCookie(response);
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(refreshExpirationMs / 1000)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractRefreshTokenCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if ("refreshToken".equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
