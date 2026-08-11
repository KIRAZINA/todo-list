package com.example.todo.controller;

import com.example.todo.entity.Task;
import com.example.todo.entity.User;
import com.example.todo.repository.TaskRepository;
import com.example.todo.repository.UserRepository;
import com.example.todo.util.RestAssuredTestBase;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the ADMIN-only /api/admin/** endpoints.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminControllerRestAssuredTest extends RestAssuredTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Admin can list users")
    void adminCanListUsers() {
        String token = createAdmin("boss");

        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/admin/users?page=0&size=20")
                .then()
                .statusCode(200)
                .body("content", notNullValue());
    }

    @Test
    @DisplayName("Admin can see tasks across all users")
    void adminCanSeeAllUsersTasks() {
        long timestamp = System.currentTimeMillis();
        // Regular user creates their own task
        String userToken = registerAndLogin("taskmaker_" + timestamp, "TaskPass123!", "taskmaker_" + timestamp + "@example.com");
        given()
                .header("Authorization", bearer(userToken))
                .contentType("application/json")
                .body("""
                    {
                        "title": "Admin's visible task",
                        "priority": "LOW",
                        "status": "TODO"
                    }
                    """)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(201);

        // Admin sees that task in the cross-user listing
        String adminToken = createAdmin("overseer");
        given()
                .header("Authorization", bearer(adminToken))
                .when()
                .get("/api/admin/tasks?page=0&size=20")
                .then()
                .statusCode(200)
                .body("totalElements", greaterThanOrEqualTo(1));
    }

    @Test
    @DisplayName("Regular user cannot access admin endpoints")
    void nonAdminGetsForbidden() {
        long timestamp = System.currentTimeMillis();
        String userToken = registerAndLogin("plainuser_" + timestamp, "PlainPass123!", "plain_" + timestamp + "@example.com");

        given()
                .header("Authorization", bearer(userToken))
                .when()
                .get("/api/admin/users")
                .then()
                .statusCode(403);

        given()
                .header("Authorization", bearer(userToken))
                .when()
                .get("/api/admin/tasks")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("Anonymous access to admin endpoints is rejected")
    void anonymousGetsUnauthorized() {
        given()
                .when()
                .get("/api/admin/users")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("Admin can promote a regular user to ADMIN")
    void adminCanChangeUserRole() {
        long timestamp = System.currentTimeMillis();
        User user = userRepository.save(User.builder()
                .username("promotable_" + timestamp)
                .password(passwordEncoder.encode("PromotePass123!"))
                .email("promotable_" + timestamp + "@example.com")
                .role("USER")
                .build());

        String adminToken = createAdmin("promoter");

        given()
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .body("{\"role\": \"ADMIN\"}")
                .when()
                .patch("/api/admin/users/" + user.getId() + "/role")
                .then()
                .statusCode(200)
                .body("role", equalTo("ADMIN"));

        // The promoted user can now reach admin endpoints with their own token
        String promotedToken = login(user.getUsername(), "PromotePass123!");
        given()
                .header("Authorization", bearer(promotedToken))
                .when()
                .get("/api/admin/users")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("Admin cannot change their own role")
    void adminCannotChangeOwnRole() {
        String username = "selfadmin_" + System.currentTimeMillis();
        User admin = userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode("AdminPass123!"))
                .email(username + "@example.com")
                .role("ADMIN")
                .build());
        String adminToken = login(username, "AdminPass123!");

        given()
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .body("{\"role\": \"USER\"}")
                .when()
                .patch("/api/admin/users/" + admin.getId() + "/role")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("Admin cannot assign an unknown role")
    void invalidRoleIsRejected() {
        long timestamp = System.currentTimeMillis();
        User user = userRepository.save(User.builder()
                .username("target_" + timestamp)
                .password(passwordEncoder.encode("Pass1234!"))
                .email("target_" + timestamp + "@example.com")
                .role("USER")
                .build());

        String adminToken = createAdmin("roler");

        given()
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .body("{\"role\": \"SUPERUSER\"}")
                .when()
                .patch("/api/admin/users/" + user.getId() + "/role")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("Regular user cannot change roles or delete users")
    void nonAdminCannotManageUsers() {
        long timestamp = System.currentTimeMillis();
        String userToken = registerAndLogin("plainuser_" + timestamp, "PlainPass123!", "plain_" + timestamp + "@example.com");

        given()
                .header("Authorization", bearer(userToken))
                .contentType("application/json")
                .body("{\"role\": \"ADMIN\"}")
                .when()
                .patch("/api/admin/users/1/role")
                .then()
                .statusCode(403);

        given()
                .header("Authorization", bearer(userToken))
                .when()
                .delete("/api/admin/users/1")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("Admin can delete a user and their tasks are removed too")
    void adminCanDeleteUserAndTheirTasks() {
        long timestamp = System.currentTimeMillis();
        User user = userRepository.save(User.builder()
                .username("delete_me_" + timestamp)
                .password(passwordEncoder.encode("DeletePass123!"))
                .email("delete_me_" + timestamp + "@example.com")
                .role("USER")
                .build());
        Task task = taskRepository.save(Task.builder()
                .title("orphaned task")
                .priority(Task.Priority.LOW)
                .status(Task.Status.TODO)
                .user(user)
                .build());

        String adminToken = createAdmin("deleter");

        given()
                .header("Authorization", bearer(adminToken))
                .when()
                .delete("/api/admin/users/" + user.getId())
                .then()
                .statusCode(204);

        assertFalse(userRepository.findById(user.getId()).isPresent());
        assertFalse(taskRepository.findById(task.getId()).isPresent());
    }

    @Test
    @DisplayName("Admin cannot delete their own account")
    void adminCannotDeleteOwnAccount() {
        String username = "selfdelete_" + System.currentTimeMillis();
        User admin = userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode("AdminPass123!"))
                .email(username + "@example.com")
                .role("ADMIN")
                .build());
        String adminToken = login(username, "AdminPass123!");

        given()
                .header("Authorization", bearer(adminToken))
                .when()
                .delete("/api/admin/users/" + admin.getId())
                .then()
                .statusCode(400);

        assertTrue(userRepository.findById(admin.getId()).isPresent());
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
