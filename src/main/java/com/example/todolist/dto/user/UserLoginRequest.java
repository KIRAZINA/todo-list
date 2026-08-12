package com.example.todolist.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * DTO for user login request.
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class UserLoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}