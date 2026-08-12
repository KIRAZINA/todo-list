package com.example.todolist.controller;

import com.example.todolist.dto.user.UserLoginRequest;
import com.example.todolist.dto.user.UserRegisterRequest;
import com.example.todolist.dto.user.UserResponse;
import com.example.todolist.security.JwtTokenProvider;
import com.example.todolist.service.TokenRevocationService;
import com.example.todolist.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody UserRegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody UserLoginRequest request) {
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
            return Map.of("token", jwt, "type", "Bearer");
        } catch (BadCredentialsException ex) {
            userService.recordFailedLogin(request.getUsername());
            throw ex;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return;
        }
        String jwt = authorizationHeader.substring(7);
        String jti = jwtTokenProvider.getJtiFromToken(jwt);
        LocalDateTime expiresAt = jwtTokenProvider.getExpirationFromToken(jwt)
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        tokenRevocationService.revoke(jti, expiresAt);
    }
}