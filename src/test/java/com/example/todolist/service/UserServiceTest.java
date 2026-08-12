package com.example.todolist.service;

import com.example.todolist.dto.user.UserRegisterRequest;
import com.example.todolist.entity.User;
import com.example.todolist.exception.IllegalAdminOperationException;
import com.example.todolist.exception.ResourceAlreadyExistsException;
import com.example.todolist.exception.ResourceNotFoundException;
import com.example.todolist.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UserService userService;

    @Test
    void shouldRegisterUserSuccessfully() {
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("encoded");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        userService.register(new UserRegisterRequest("john", "pass123", "john@example.com"));

        verify(passwordEncoder).encode("pass123");
        verify(userRepository).save(argThat(u ->
                u.getUsername().equals("john")
                        && u.getEmail().equals("john@example.com")
                        && u.getPassword().equals("encoded")
                        && u.getRole().equals("USER")
        ));
    }

    @Test
    void shouldRejectDuplicateUsername() {
        when(userRepository.existsByUsername("john")).thenReturn(true);

        assertThrows(ResourceAlreadyExistsException.class, () ->
                userService.register(new UserRegisterRequest("john", "pass123", "john@example.com")));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldRejectDuplicateEmail() {
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThrows(ResourceAlreadyExistsException.class, () ->
                userService.register(new UserRegisterRequest("john", "pass123", "john@example.com")));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldFindUserByUsername() {
        User user = User.builder().id(1L).username("john").build();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        User result = userService.findByUsername("john");

        assertEquals(1L, result.getId());
        assertEquals("john", result.getUsername());
    }

    @Test
    void shouldThrowWhenUserNotFoundByUsername() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.findByUsername("missing"));
    }

    @Test
    void shouldThrowWhenUserNotFoundById() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(99L));
    }

    @Test
    void shouldLockAccountAfterMaxFailedAttempts() {
        User user = User.builder()
                .id(1L)
                .username("john")
                .failedLoginAttempts(0)
                .build();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        for (int i = 0; i < 5; i++) {
            userService.recordFailedLogin("john");
        }

        assertEquals(5, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil());
        verify(userRepository, times(5)).save(user);
    }

    @Test
    void shouldNotLockBeforeThreshold() {
        User user = User.builder()
                .id(1L)
                .username("john")
                .failedLoginAttempts(0)
                .build();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        userService.recordFailedLogin("john");
        userService.recordFailedLogin("john");

        assertEquals(2, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
    }

    @Test
    void shouldResetFailedLoginOnSuccess() {
        User user = User.builder()
                .id(1L)
                .username("john")
                .failedLoginAttempts(3)
                .lockedUntil(LocalDateTime.now().plusHours(1))
                .build();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        userService.resetFailedLogin("john");

        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
        verify(userRepository).save(user);
    }

    @Test
    void shouldNotRecordFailedLoginForUnknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> userService.recordFailedLogin("ghost"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldRejectDemotingTheLastAdmin() {
        User target = User.builder().id(2L).username("admin2").role("ADMIN").build();
        User currentAdmin = User.builder().id(1L).username("admin1").role("ADMIN").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRole("ADMIN")).thenReturn(1L);

        assertThrows(IllegalAdminOperationException.class,
                () -> userService.updateUserRole(2L, "USER", currentAdmin));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldAllowDemotingAdminWhenAnotherRemains() {
        User target = User.builder().id(2L).username("admin2").role("ADMIN").build();
        User currentAdmin = User.builder().id(1L).username("admin1").role("ADMIN").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRole("ADMIN")).thenReturn(2L);
        when(userRepository.save(target)).thenReturn(target);

        userService.updateUserRole(2L, "USER", currentAdmin);

        assertEquals("USER", target.getRole());
        verify(userRepository).countByRole("ADMIN");
        verify(userRepository).save(target);
    }

    @Test
    void shouldRejectDeletingTheLastAdmin() {
        User target = User.builder().id(2L).username("admin2").role("ADMIN").build();
        User currentAdmin = User.builder().id(1L).username("admin1").role("ADMIN").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRole("ADMIN")).thenReturn(1L);

        assertThrows(IllegalAdminOperationException.class,
                () -> userService.deleteUser(2L, currentAdmin));

        verify(userRepository, never()).delete(any());
    }

    @Test
    void shouldAllowDeletingAdminWhenAnotherRemains() {
        User target = User.builder().id(2L).username("admin2").role("ADMIN").build();
        User currentAdmin = User.builder().id(1L).username("admin1").role("ADMIN").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRole("ADMIN")).thenReturn(2L);

        userService.deleteUser(2L, currentAdmin);

        verify(userRepository).delete(target);
    }

    @Test
    void shouldNotQueryAdminCountForNonAdminTarget() {
        User target = User.builder().id(2L).username("user2").role("USER").build();
        User currentAdmin = User.builder().id(1L).username("admin1").role("ADMIN").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        userService.updateUserRole(2L, "ADMIN", currentAdmin);

        verify(userRepository, never()).countByRole(anyString());
    }
}
