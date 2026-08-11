package com.example.todo.config;

import com.example.todo.entity.User;
import com.example.todo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time bootstrap for the very first ADMIN account.
 *
 * <p>Activated only when ADMIN_PASSWORD is provided (via env vars in the
 * Docker setup, or any Spring property source). If the username already
 * exists the bootstrap is a no-op, so subsequent restarts are safe.
 * Registration via the public API only ever creates USER accounts, which is
 * why this explicit bootstrap hook is needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BootstrapAdminRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_USERNAME:admin}")
    private String adminUsername;

    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    @Value("${ADMIN_EMAIL:admin@example.com}")
    private String adminEmail;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.info("Bootstrap admin skipped: no ADMIN_PASSWORD configured");
            return;
        }
        if (userRepository.existsByUsername(adminUsername)) {
            log.info("Bootstrap admin skipped: user '{}' already exists", adminUsername);
            return;
        }
        User admin = User.builder()
                .username(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .email(adminEmail)
                .role("ADMIN")
                .build();
        userRepository.save(admin);
        log.info("Bootstrapped initial admin user '{}'", adminUsername);
    }
}
