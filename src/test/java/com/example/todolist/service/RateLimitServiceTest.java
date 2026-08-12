package com.example.todolist.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitServiceTest {

    @Test
    void shouldAllowRequestsWithinBudget() {
        RateLimitService service = new RateLimitService(true, 3, 60_000);

        assertTrue(service.isAllowed("1.2.3.4"));
        assertTrue(service.isAllowed("1.2.3.4"));
        assertTrue(service.isAllowed("1.2.3.4"));
        assertFalse(service.isAllowed("1.2.3.4"));
    }

    @Test
    void shouldTrackClientsIndependently() {
        RateLimitService service = new RateLimitService(true, 1, 60_000);

        assertTrue(service.isAllowed("a"));
        assertFalse(service.isAllowed("a"));
        assertTrue(service.isAllowed("b"));
    }

    @Test
    void shouldResetAfterWindowElapses() throws InterruptedException {
        RateLimitService service = new RateLimitService(true, 1, 50);

        assertTrue(service.isAllowed("1.2.3.4"));
        assertFalse(service.isAllowed("1.2.3.4"));

        Thread.sleep(60);
        assertTrue(service.isAllowed("1.2.3.4"));
    }

    @Test
    void shouldNotLimitWhenDisabled() {
        RateLimitService service = new RateLimitService(false, 1, 60_000);

        assertTrue(service.isAllowed("1.2.3.4"));
        assertTrue(service.isAllowed("1.2.3.4"));
    }
}
