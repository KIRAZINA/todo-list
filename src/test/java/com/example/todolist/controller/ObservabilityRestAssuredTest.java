package com.example.todolist.controller;

import com.example.todolist.entity.User;
import com.example.todolist.repository.UserRepository;
import com.example.todolist.util.RestAssuredTestBase;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ObservabilityRestAssuredTest extends RestAssuredTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Actuator health is public and hides details without authentication")
    void healthIsPublicAndHidesDetails() {
        given()
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("$", not(hasKey("components")));
    }

    @Test
    @DisplayName("Actuator health shows components to an ADMIN")
    void healthShowsDetailsToAdmin() {
        String adminToken = createAdmin("health_admin");

        given()
                .header("Authorization", bearer(adminToken))
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("components", notNullValue());
    }

    @Test
    @DisplayName("Actuator info is public and exposes the app name")
    void infoIsPublic() {
        given()
                .when()
                .get("/actuator/info")
                .then()
                .statusCode(200)
                .body("app.name", equalTo("todo-list"));
    }

    @Test
    @DisplayName("Actuator metrics is ADMIN-only")
    void metricsRequiresAdmin() {
        // Unauthenticated -> 401
        given()
                .when()
                .get("/actuator/metrics")
                .then()
                .statusCode(401);

        // USER -> 403
        String userToken = registerAndLogin("metric_user", "SecurePass123!", "metric@example.com");
        given()
                .header("Authorization", bearer(userToken))
                .when()
                .get("/actuator/metrics")
                .then()
                .statusCode(403);

        // ADMIN -> 200 with a names array
        String adminToken = createAdmin("metrics_admin");
        given()
                .header("Authorization", bearer(adminToken))
                .when()
                .get("/actuator/metrics")
                .then()
                .statusCode(200)
                .body("names", notNullValue());
    }

    @Test
    @DisplayName("Actuator prometheus is ADMIN-only and serves the text format")
    void prometheusRequiresAdmin() {
        String adminToken = createAdmin("prom_admin");

        given()
                .header("Authorization", bearer(adminToken))
                .when()
                .get("/actuator/prometheus")
                .then()
                .statusCode(200)
                .contentType(notNullValue())
                .body(notNullValue());
    }

    @Test
    @DisplayName("Actuator discovery page is ADMIN-only")
    void actuatorDiscoveryRequiresAdmin() {
        given()
                .when()
                .get("/actuator")
                .then()
                .statusCode(401);

        String adminToken = createAdmin("disco_admin");
        given()
                .header("Authorization", bearer(adminToken))
                .when()
                .get("/actuator")
                .then()
                .statusCode(200);
    }

    private String createAdmin(String baseUsername) {
        long timestamp = System.currentTimeMillis();
        String username = baseUsername + "_" + timestamp;
        String email = baseUsername + "_" + timestamp + "@example.com";

        userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode("AdminPass123!"))
                .email(email)
                .role("ADMIN")
                .build());

        Response response = given()
                .body(String.format("""
                    {
                        "username": "%s",
                        "password": "AdminPass123!"
                    }
                    """, username))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .response();
        return response.jsonPath().getString("token");
    }
}