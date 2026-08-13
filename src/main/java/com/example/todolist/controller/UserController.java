package com.example.todolist.controller;

import com.example.todolist.dto.user.EmailUpdateRequest;
import com.example.todolist.dto.user.PasswordChangeRequest;
import com.example.todolist.dto.user.UserResponse;
import com.example.todolist.entity.User;
import com.example.todolist.exception.UserNotAuthenticatedException;
import com.example.todolist.security.CurrentUserService;
import com.example.todolist.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CurrentUserService currentUserService;

    @GetMapping("/me")
    public UserResponse getMe() {
        return userService.getCurrentUserProfile(currentUser());
    }

    @PatchMapping("/me")
    public UserResponse updateEmail(@Valid @RequestBody EmailUpdateRequest request) {
        return userService.updateEmail(currentUser(), request);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(currentUser(), request);
    }

    private User currentUser() {
        return currentUserService.getCurrentUser()
                .orElseThrow(() -> new UserNotAuthenticatedException("User not authenticated"));
    }
}
