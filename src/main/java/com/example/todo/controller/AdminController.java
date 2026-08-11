package com.example.todo.controller;

import com.example.todo.dto.task.PaginatedTaskResponse;
import com.example.todo.dto.user.PaginatedUserResponse;
import com.example.todo.dto.user.RoleUpdateRequest;
import com.example.todo.dto.user.UserResponse;
import com.example.todo.entity.User;
import com.example.todo.exception.UserNotAuthenticatedException;
import com.example.todo.security.CurrentUserService;
import com.example.todo.service.TaskService;
import com.example.todo.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN-only endpoints: user management and visibility into every task
 * across all users. Everything under /api/admin/** is gated by
 * hasRole("ADMIN") in SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final TaskService taskService;
    private final CurrentUserService currentUserService;

    @GetMapping("/users")
    public PaginatedUserResponse getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int normalizedSize = Math.max(1, Math.min(size, 100));
        return userService.getUsersPaginated(page, normalizedSize);
    }

    @GetMapping("/tasks")
    public PaginatedTaskResponse getAllTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int normalizedSize = Math.max(1, Math.min(size, 100));
        return taskService.getAllTasksPaginated(page, normalizedSize);
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<UserResponse> updateRole(@PathVariable Long id,
                                                   @Valid @RequestBody RoleUpdateRequest request) {
        User currentAdmin = currentUserService.getCurrentUser()
                .orElseThrow(() -> new UserNotAuthenticatedException("User not authenticated"));
        return ResponseEntity.ok(userService.updateUserRole(id, request.getRole(), currentAdmin));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        User currentAdmin = currentUserService.getCurrentUser()
                .orElseThrow(() -> new UserNotAuthenticatedException("User not authenticated"));
        userService.deleteUser(id, currentAdmin);
        return ResponseEntity.noContent().build();
    }
}
