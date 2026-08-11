package com.example.todo.api;

import com.example.todo.entity.User;
import com.example.todo.repository.UserRepository;
import com.example.todo.security.CustomUserDetails;
import com.example.todo.security.JwtTokenProvider;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression guard for R2 Stage-1: the per-request identity layer must stay at
 * exactly two DB queries (revocation check + user load). Relies on Hibernate
 * statistics (hibernate.generate_statistics) to count prepared statements.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticatedRequestQueryCountTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void identityResolutionIsExactlyTwoQueries() throws Exception {
        String token = createUserAndToken();
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        long before = stats.getPrepareStatementCount();
        mockMvc.perform(get("/api/test/health").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long after = stats.getPrepareStatementCount();

        // isRevoked (revoked_tokens) + findByUsername (users) = 2. The health
        // endpoint does no DB work, so this isolates the identity layer exactly.
        assertThat(after - before).isEqualTo(2);
    }

    @Test
    void authenticatedTaskListRequestIsExactlyThreeQueries() throws Exception {
        String token = createUserAndToken();
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        long before = stats.getPrepareStatementCount();
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long after = stats.getPrepareStatementCount();

        // identity (2) + findByUser select (1) = 3. The page count query is
        // skipped because the result set is empty (fewer rows than the page
        // size) via Spring Data's PageableExecutionUtils last-page optimization.
        // A regression that re-adds a per-request user lookup (R2 Stage-1)
        // makes this 4.
        assertThat(after - before).isEqualTo(3);
    }

    private String createUserAndToken() {
        User user = userRepository.save(User.builder()
                .username("qcount_" + System.nanoTime())
                .password(passwordEncoder.encode("Password123"))
                .email("qcount_" + System.nanoTime() + "@example.com")
                .role("USER")
                .build());
        CustomUserDetails details = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        return jwtTokenProvider.generateToken(auth);
    }
}
