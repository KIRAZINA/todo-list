package com.example.todolist.controller;

import com.example.todolist.util.RestAssuredTestBase;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RestAssured integration tests for AuthController API endpoints.
 * Tests user registration, login, and authentication functionality.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthControllerRestAssuredTest extends RestAssuredTestBase {

    @Test
    @DisplayName("Should register user successfully with valid data")
    void shouldRegisterUserSuccessfully() {
        long timestamp = System.currentTimeMillis();
        String requestBody = String.format("""
            {
                "username": "john_doe_%d",
                "password": "SecurePass123!",
                "email": "john.doe.%d@example.com"
            }
            """, timestamp, timestamp);

        given()
                .body(requestBody)
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .contentType("application/json")
                .body("username", equalTo("john_doe_" + timestamp))
                .body("email", equalTo("john.doe." + timestamp + "@example.com"))
                .body("$", hasKey("id"))
                .body("$", hasKey("createdAt"));
    }

    @Test
    @DisplayName("Should reject registration with duplicate username")
    void shouldNotRegisterDuplicateUsername() {
        long timestamp = System.currentTimeMillis();
        String requestBody = String.format("""
            {
                "username": "duplicate_user_%d",
                "password": "Password123!",
                "email": "first.%d@example.com"
            }
            """, timestamp, timestamp);

        // First registration should succeed
        given()
                .body(requestBody)
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201);

        // Second registration with same username should fail
        given()
                .body(requestBody)
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(409)
                .contentType("application/json")
                .body("error", notNullValue())
                .body("error", equalTo("Username already exists"));
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void shouldLoginSuccessfully() {
        long timestamp = System.currentTimeMillis();
        // Register user first
        String registerBody = String.format("""
            {
                "username": "alice_wonder_%d",
                "password": "AliceSecret123!",
                "email": "alice.%d@example.com"
            }
            """, timestamp, timestamp);

        given()
                .body(registerBody)
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201);

        // Login with valid credentials
        String loginBody = String.format("""
            {
                "username": "alice_wonder_%d",
                "password": "AliceSecret123!"
            }
            """, timestamp);

        Response response = given()
                .body(loginBody)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("token", notNullValue())
                .body("token", matchesPattern("^[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*$"))
                .body("type", equalTo("Bearer"))
                .extract()
                .response();

        // Verify token structure (JWT should have 3 parts separated by dots)
        String token = response.jsonPath().getString("token");
        String[] tokenParts = token.split("\\.");
        assertEquals(3, tokenParts.length, "JWT token should have 3 parts");
    }

    @Test
    @DisplayName("Should reject login with invalid credentials")
    void shouldRejectInvalidCredentials() {
        String loginBody = """
            {
                "username": "nonexistent_user",
                "password": "wrong_password"
            }
            """;

        given()
                .body(loginBody)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("Should reject registration with invalid data")
    void shouldRejectInvalidRegistrationData() {
        // Test empty username
        given()
                .body("""
                    {
                        "username": "",
                        "password": "ValidPass123!",
                        "email": "test@example.com"
                    }
                    """)
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(400);

        // Test empty password
        given()
                .body("""
                    {
                        "username": "testuser",
                        "password": "",
                        "email": "test@example.com"
                    }
                    """)
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(400);

        // Test invalid email format
        given()
                .body("""
                    {
                        "username": "testuser",
                        "password": "ValidPass123!",
                        "email": "invalid-email"
                    }
                    """)
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("Should reject login with invalid data")
    void shouldRejectInvalidLoginData() {
        // Test empty username
        given()
                .body("""
                    {
                        "username": "",
                        "password": "somepassword"
                    }
                    """)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(400);

        // Test empty password
        given()
                .body("""
                    {
                        "username": "testuser",
                        "password": ""
                    }
                    """)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("Should reject login with wrong password for existing user")
    void shouldRejectLoginWithWrongPassword() {
        long timestamp = System.currentTimeMillis();
        // Register user
        given()
                .body(String.format("""
                    {
                        "username": "correctuser_%d",
                        "password": "CorrectPass123!",
                        "email": "correct.%d@example.com"
                    }
                    """, timestamp, timestamp))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201);

        // Try login with wrong password
        given()
                .body(String.format("""
                    {
                        "username": "correctuser_%d",
                        "password": "wrongpass"
                    }
                    """, timestamp))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("Should reject registration with weak password (no uppercase/digit mix)")
    void shouldRejectWeakPassword() {
        long timestamp = System.currentTimeMillis();
        // lowercase + digits only вЂ” fails the new password policy
        given()
                .body(String.format("""
                    {
                        "username": "weakpw_user_%d",
                        "password": "lowercaseanddigits123",
                        "email": "weak.%d@example.com"
                    }
                    """, timestamp, timestamp))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(400);

        // mixed case but no digit
        given()
                .body(String.format("""
                    {
                        "username": "weakpw_user_%d",
                        "password": "OnlyLettersHere",
                        "email": "weak2.%d@example.com"
                    }
                    """, timestamp, timestamp))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("Should lock the account after repeated failed logins")
    void shouldLockAccountAfterRepeatedFailedLogins() {
        long timestamp = System.currentTimeMillis();
        String username = "lock_user_" + timestamp;

        given()
                .body(String.format("""
                    {
                        "username": "%s",
                        "password": "LockPass123!",
                        "email": "lock.%d@example.com"
                    }
                    """, username, timestamp))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201);

        // 5 consecutive failures lock the account
        for (int i = 0; i < 5; i++) {
            given()
                    .body(String.format("""
                        {
                            "username": "%s",
                            "password": "WrongPass123"
                        }
                        """, username))
                    .when()
                    .post("/api/auth/login")
                    .then()
                    .statusCode(401);
        }

        // Even a correct password is rejected while the account is locked
        given()
                .body(String.format("""
                    {
                        "username": "%s",
                        "password": "LockPass123!"
                    }
                    """, username))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(423);
    }

    @Test
    @DisplayName("Should handle malformed JSON requests gracefully")
    void shouldHandleMalformedJsonRequests() {
        given()
                .body("{invalid json}")
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(400);

        given()
                .body("{invalid json}")
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("Should validate response time for authentication endpoints")
    void shouldValidateResponseTime() {
        long timestamp = System.currentTimeMillis();
        String registerBody = String.format("""
            {
                "username": "performance_user_%d",
                "password": "PerfTest123!",
                "email": "perf.%d@example.com"
            }
            """, timestamp, timestamp);

        // Registration should complete within reasonable time
        given()
                .body(registerBody)
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .time(lessThan(2000L)); // Less than 2 seconds

        // Login should complete within reasonable time
        given()
                .body(String.format("""
                    {
                        "username": "performance_user_%d",
                        "password": "PerfTest123!"
                    }
                    """, timestamp))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .time(lessThan(1000L)); // Less than 1 second
    }
}
