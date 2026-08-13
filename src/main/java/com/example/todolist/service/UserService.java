package com.example.todolist.service;

import com.example.todolist.dto.user.EmailUpdateRequest;
import com.example.todolist.dto.user.PaginatedUserResponse;
import com.example.todolist.dto.user.PasswordChangeRequest;
import com.example.todolist.dto.user.UserRegisterRequest;
import com.example.todolist.dto.user.UserResponse;
import com.example.todolist.entity.User;
import com.example.todolist.exception.IllegalAdminOperationException;
import com.example.todolist.exception.ResourceAlreadyExistsException;
import com.example.todolist.exception.ResourceNotFoundException;
import com.example.todolist.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${security.lockout.max-attempts:5}")
    private int maxLoginAttempts = 5;

    @Value("${security.lockout.duration-ms:900000}")
    private long lockoutDurationMs = 900000;

    @Transactional
    public UserResponse register(UserRegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResourceAlreadyExistsException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceAlreadyExistsException("Email already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role("USER")
                .build();

        user = userRepository.save(user);
        log.info("New user created: {} (ID: {})", user.getUsername(), user.getId());
        return toResponse(user);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public void recordFailedLogin(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            if (user.getFailedLoginAttempts() >= maxLoginAttempts) {
                user.setLockedUntil(LocalDateTime.now().plusNanos(lockoutDurationMs * 1_000_000L));
                log.warn("Account '{}' locked until {}", user.getUsername(), user.getLockedUntil());
            }
            userRepository.save(user);
        });
    }

    @Transactional
    public void resetFailedLogin(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        });
    }

    @Transactional(readOnly = true)
    public PaginatedUserResponse getUsersPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        Page<User> userPage = userRepository.findAll(pageable);

        List<UserResponse> content = userPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PaginatedUserResponse.builder()
                .content(content)
                .number(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .first(userPage.isFirst())
                .last(userPage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateUserRole(Long targetUserId, String newRole, User currentAdmin) {
        if (newRole == null || !(newRole.equals("USER") || newRole.equals("ADMIN"))) {
            throw new IllegalArgumentException("Role must be USER or ADMIN");
        }
        if (targetUserId.equals(currentAdmin.getId())) {
            throw new IllegalAdminOperationException("Cannot change your own role via this endpoint");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ensureNotLastAdmin(target, !newRole.equals("ADMIN"));
        target.setRole(newRole);
        target = userRepository.save(target);
        log.info("Admin '{}' changed role of user '{}' to {}", currentAdmin.getUsername(), target.getUsername(), newRole);
        return toResponse(target);
    }

    @Transactional
    public void deleteUser(Long targetUserId, User currentAdmin) {
        if (targetUserId.equals(currentAdmin.getId())) {
            throw new IllegalAdminOperationException("Cannot delete your own account via this endpoint");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ensureNotLastAdmin(target, true);
        userRepository.delete(target);
        log.info("Admin '{}' deleted user '{}'", currentAdmin.getUsername(), target.getUsername());
    }

    // Defense-in-depth: authorization re-derives the caller's role from the DB on
    // every request, so a demoted/deleted admin is locked out of /api/admin/**
    // immediately and a consistent DB can never reach zero admins through these
    // sequential paths (self-actions are blocked above). This guard documents that
    // invariant explicitly. It does NOT close the concurrent TOCTOU race where two
    // admins demote/delete each other in parallel вЂ” that would need an atomic
    // conditional update or a lock.
    private void ensureNotLastAdmin(User target, boolean removingAdmin) {
        if (!removingAdmin || !target.getRole().equals("ADMIN")) {
            return;
        }
        long adminCount = userRepository.countByRole("ADMIN");
        if (adminCount <= 1) {
            throw new IllegalAdminOperationException("Cannot demote or delete the last remaining ADMIN");
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUserProfile(User user) {
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateEmail(User user, EmailUpdateRequest request) {
        String newEmail = request.getEmail();
        if (user.getEmail().equals(newEmail)) {
            return toResponse(user);
        }
        if (userRepository.existsByEmail(newEmail)) {
            throw new ResourceAlreadyExistsException("Email already exists");
        }
        user.setEmail(newEmail);
        user = userRepository.save(user);
        log.info("User '{}' updated email", user.getUsername());
        return toResponse(user);
    }

    @Transactional
    public void changePassword(User user, PasswordChangeRequest request) {
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("User '{}' changed password", user.getUsername());
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
