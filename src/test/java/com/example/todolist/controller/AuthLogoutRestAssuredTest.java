package com.example.todolist.controller;

import com.example.todolist.util.RestAssuredTestBase;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Integration tests for server-side token revocation via /api/auth/logout.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthLogoutRestAssuredTest extends RestAssuredTestBase {

    @Test
    @DisplayName("Logout should revoke the token server-side")
    void shouldRevokeTokenOnLogout() {
        long timestamp = System.currentTimeMillis();
        String username = "logout_user_" + timestamp;
        String password = "LogoutPass123!";

        given()
                .body(String.format("""
                    {
                        "username": "%s",
                        "password": "%s",
                        "email": "logout.%d@example.com"
                    }
                    """, username, password, timestamp))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201);

        String token = loginToken(username, password);

        // Token works before logout
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200);

        // Logout revokes it
        given()
                .header("Authorization", bearer(token))
                .when()
                .post("/api/auth/logout")
                .then()
                .statusCode(204);

        // The same token is now rejected
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(401);

        // A fresh login yields a working token
        String newToken = loginToken(username, password);
        given()
                .header("Authorization", bearer(newToken))
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200);
    }

    private String loginToken(String username, String password) {
        Response response = given()
                .body(String.format("""
                    {
                        "username": "%s",
                        "password": "%s"
                    }
                    """, username, password))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .extract()
                .response();
        return response.jsonPath().getString("token");
    }
}
