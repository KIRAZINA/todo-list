package com.example.todolist.controller;

import com.example.todolist.util.RestAssuredTestBase;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserControllerRestAssuredTest extends RestAssuredTestBase {

    private record TestUser(String username, String password, String email, String token) {}

    private TestUser registerUser(String baseUsername, String password, String email) {
        long ts = System.currentTimeMillis();
        String username = baseUsername + "_" + ts;
        String uniqueEmail = email.replace("@", "_" + ts + "@");

        given()
                .body(String.format("""
                    {
                        "username": "%s",
                        "password": "%s",
                        "email": "%s"
                    }
                    """, username, password, uniqueEmail))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201);

        return new TestUser(username, password, uniqueEmail, login(username, password));
    }

    private String login(String username, String password) {
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
                .extract()
                .response();
        return response.jsonPath().getString("token");
    }

    @Test
    @DisplayName("GET /api/users/me returns the caller's profile")
    void shouldReturnCurrentUserProfile() {
        TestUser user = registerUser("me_user", "SecurePass123!", "me@example.com");

        given()
                .header("Authorization", bearer(user.token()))
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(200)
                .body("username", equalTo(user.username()))
                .body("email", equalTo(user.email()))
                .body("role", equalTo("USER"))
                .body("id", notNullValue())
                .body("createdAt", notNullValue());
    }

    @Test
    @DisplayName("GET /api/users/me requires authentication")
    void shouldRejectMeWithoutToken() {
        given()
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(401)
                .body("error", equalTo("Authentication required"));
    }

    @Test
    @DisplayName("PATCH /api/users/me with a new unique email succeeds and persists")
    void shouldUpdateEmailSuccessfully() {
        TestUser user = registerUser("mail_user", "SecurePass123!", "before@example.com");

        given()
                .header("Authorization", bearer(user.token()))
                .body("""
                    {
                        "email": "after@example.com"
                    }
                    """)
                .when()
                .patch("/api/users/me")
                .then()
                .statusCode(200)
                .body("email", equalTo("after@example.com"))
                .body("username", equalTo(user.username()));

        given()
                .header("Authorization", bearer(user.token()))
                .when()
                .get("/api/users/me")
                .then()
                .statusCode(200)
                .body("email", equalTo("after@example.com"));
    }

    @Test
    @DisplayName("PATCH /api/users/me with an in-use email returns 409")
    void shouldRejectDuplicateEmail() {
        TestUser first = registerUser("dup_a", "SecurePass123!", "taken@example.com");
        TestUser second = registerUser("dup_b", "SecurePass123!", "other@example.com");

        given()
                .header("Authorization", bearer(second.token()))
                .body(String.format("""
                    {
                        "email": "%s"
                    }
                    """, first.email()))
                .when()
                .patch("/api/users/me")
                .then()
                .statusCode(409)
                .body("error", equalTo("Email already exists"));
    }

    @Test
    @DisplayName("PATCH /api/users/me with an invalid email returns 400")
    void shouldRejectInvalidEmail() {
        TestUser user = registerUser("badmail", "SecurePass123!", "ok@example.com");

        given()
                .header("Authorization", bearer(user.token()))
                .body("""
                    {
                        "email": "not-an-email"
                    }
                    """)
                .when()
                .patch("/api/users/me")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("PUT /api/users/me/password with correct current password succeeds; new login works, old fails")
    void shouldChangePasswordAndUpdateLoginCredentials() {
        TestUser user = registerUser("pwd_user", "OldPass123!", "pwd@example.com");

        given()
                .header("Authorization", bearer(user.token()))
                .body("""
                    {
                        "currentPassword": "OldPass123!",
                        "newPassword": "NewPass456!"
                    }
                    """)
                .when()
                .put("/api/users/me/password")
                .then()
                .statusCode(204);

        // Old password no longer authenticates.
        given()
                .body(String.format("""
                    {
                        "username": "%s",
                        "password": "OldPass123!"
                    }
                    """, user.username()))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(401);

        // New password authenticates.
        String newToken = login(user.username(), "NewPass456!");
        assertNotEquals(user.token(), newToken);
    }

    @Test
    @DisplayName("PUT /api/users/me/password with wrong current password returns 400")
    void shouldRejectWrongCurrentPassword() {
        TestUser user = registerUser("pwd_wrong", "OldPass123!", "pwdw@example.com");

        given()
                .header("Authorization", bearer(user.token()))
                .body("""
                    {
                        "currentPassword": "WrongPass123!",
                        "newPassword": "NewPass456!"
                    }
                    """)
                .when()
                .put("/api/users/me/password")
                .then()
                .statusCode(400)
                .body("error", equalTo("Current password is incorrect"));
    }

    @Test
    @DisplayName("PUT /api/users/me/password with a weak new password returns 400")
    void shouldRejectWeakNewPassword() {
        TestUser user = registerUser("pwd_weak", "OldPass123!", "pwdweak@example.com");

        given()
                .header("Authorization", bearer(user.token()))
                .body("""
                    {
                        "currentPassword": "OldPass123!",
                        "newPassword": "weak"
                    }
                    """)
                .when()
                .put("/api/users/me/password")
                .then()
                .statusCode(400);
    }
}
