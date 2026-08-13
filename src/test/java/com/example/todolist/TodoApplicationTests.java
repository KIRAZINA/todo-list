package com.example.todolist;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic integration test to verify the Spring application context loads successfully.
 */
@SpringBootTest
@ActiveProfiles("test")
class TodoApplicationTests {

    /**
     * Test method to check if the context loads without errors.
     */
    @Test
    void contextLoads() {
        // No assertions needed; if context loads, test passes
    }
}