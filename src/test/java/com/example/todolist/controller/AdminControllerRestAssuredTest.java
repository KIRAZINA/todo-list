package com.example.todolist.controller;

import com.example.todolist.entity.Task;
import com.example.todolist.entity.User;
import com.example.todolist.repository.TaskRepository;
import com.example.todolist.repository.UserRepository;
import com.example.todolist.util.RestAssuredTestBase;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    @DisplayName("Admin can filter tasks across users by status and priority")
    void adminCanFilterTasksAcrossUsers() {
        long ts = System.currentTimeMillis();
        User alice = saveUser("filter_alice");
        User bob = saveUser("filter_bob");

        Task a1 = saveTask(alice, "alpha-done-" + ts, Task.Priority.LOW, Task.Status.DONE, null);
        Task a2 = saveTask(alice, "alpha-todo-" + ts, Task.Priority.HIGH, Task.Status.TODO, null);
        Task b1 = saveTask(bob, "beta-done-" + ts, Task.Priority.MEDIUM, Task.Status.DONE, null);
        Task b2 = saveTask(bob, "beta-todo-" + ts, Task.Priority.LOW, Task.Status.TODO, null);

        String adminToken = createAdmin("filter_admin");

        given()
                .header("Authorization", bearer(adminToken))
                .queryParam("status", "DONE")
                .when()
                .get("/api/admin/tasks")
                .then()
                .statusCode(200)
                .body("content.title", hasItems(a1.getTitle(), b1.getTitle()))
                .body("content.title", not(hasItem(a2.getTitle())))
                .body("content.title", not(hasItem(b2.getTitle())));

        given()
                .header("Authorization", bearer(adminToken))
                .queryParam("priority", "LOW")
                .when()
                .get("/api/admin/tasks")
                .then()
                .statusCode(200)
                .body("content.title", hasItems(a1.getTitle(), b2.getTitle()))
                .body("content.title", not(hasItem(a2.getTitle())))
                .body("content.title", not(hasItem(b1.getTitle())));

        given()
                .header("Authorization", bearer(adminToken))
                .queryParam("status", "DONE")
                .queryParam("priority", "MEDIUM")
                .when()
                .get("/api/admin/tasks")
                .then()
                .statusCode(200)
                .body("content.title", hasItems(b1.getTitle()))
                .body("content.title", not(hasItem(a1.getTitle())))
                .body("content.title", not(hasItem(a2.getTitle())))
                .body("content.title", not(hasItem(b2.getTitle())));
    }

    @Test
    @DisplayName("Admin overdue filter applies across users and ignores completed tasks")
    void adminCanFilterOverdueTasksAcrossUsers() {
        long ts = System.currentTimeMillis();
        User carol = saveUser("filter_carol");
        User dave = saveUser("filter_dave");
        User eve = saveUser("filter_eve");

        Task past = saveTask(carol, "carol-past-" + ts, Task.Priority.LOW, Task.Status.TODO,
                LocalDate.now().minusDays(1));
        Task future = saveTask(dave, "dave-future-" + ts, Task.Priority.LOW, Task.Status.TODO,
                LocalDate.now().plusDays(1));
        Task donePast = saveTask(eve, "eve-done-past-" + ts, Task.Priority.LOW, Task.Status.DONE,
                LocalDate.now().minusDays(1));

        String adminToken = createAdmin("filter_admin");

        given()
                .header("Authorization", bearer(adminToken))
                .queryParam("overdue", "true")
                .when()
                .get("/api/admin/tasks")
                .then()
                .statusCode(200)
                .body("content.title", hasItems(past.getTitle()))
                .body("content.title", not(hasItem(future.getTitle())))
                .body("content.title", not(hasItem(donePast.getTitle())));
    }

    @Test
    @DisplayName("Admin can filter tasks across users by due date range")
    void adminCanFilterTasksByDueDateRange() {
        long ts = System.currentTimeMillis();
        User frank = saveUser("filter_frank");
        User grace = saveUser("filter_grace");
        User heidi = saveUser("filter_heidi");

        Task past = saveTask(frank, "frank-past-" + ts, Task.Priority.LOW, Task.Status.TODO,
                LocalDate.now().minusDays(1));
        Task today = saveTask(frank, "frank-today-" + ts, Task.Priority.LOW, Task.Status.TODO, LocalDate.now());
        Task tomorrow = saveTask(grace, "grace-tomorrow-" + ts, Task.Priority.LOW, Task.Status.TODO,
                LocalDate.now().plusDays(1));
        Task far = saveTask(heidi, "heidi-far-" + ts, Task.Priority.LOW, Task.Status.TODO,
                LocalDate.now().plusDays(10));

        String adminToken = createAdmin("filter_admin");

        given()
                .header("Authorization", bearer(adminToken))
                .queryParam("dueBefore", LocalDate.now().plusDays(1).toString())
                .when()
                .get("/api/admin/tasks")
                .then()
                .statusCode(200)
                .body("content.title", hasItems(today.getTitle(), tomorrow.getTitle()))
                .body("content.title", not(hasItem(far.getTitle())));

        given()
                .header("Authorization", bearer(adminToken))
                .queryParam("dueAfter", LocalDate.now().toString())
                .when()
                .get("/api/admin/tasks")
                .then()
                .statusCode(200)
                .body("content.title", hasItems(today.getTitle(), tomorrow.getTitle(), far.getTitle()))
                .body("content.title", not(hasItem(past.getTitle())));

        given()
                .header("Authorization", bearer(adminToken))
                .queryParam("dueAfter", LocalDate.now().toString())
                .queryParam("dueBefore", LocalDate.now().plusDays(1).toString())
                .when()
                .get("/api/admin/tasks")
                .then()
                .statusCode(200)
                .body("content.title", hasItems(today.getTitle(), tomorrow.getTitle()))
                .body("content.title", not(hasItem(past.getTitle())))
                .body("content.title", not(hasItem(far.getTitle())));
    }

    @Test
    @DisplayName("Admin can sort tasks across users and sees ownerUsername on each task")
    void adminCanSortAndSeeOwnerUsername() {
        long ts = System.currentTimeMillis();
        User ivan = saveUser("sort_ivan");
        User judy = saveUser("sort_judy");

        Task sortA = saveTask(ivan, "sort-a-" + ts, Task.Priority.LOW, Task.Status.TODO, null);
        Task sortB = saveTask(judy, "sort-b-" + ts, Task.Priority.LOW, Task.Status.TODO, null);
        Task sortC = saveTask(ivan, "sort-c-" + ts, Task.Priority.LOW, Task.Status.TODO, null);

        String adminToken = createAdmin("sort_admin");

        List<String> titles = given()
                .header("Authorization", bearer(adminToken))
                .queryParam("sortBy", "title")
                .queryParam("direction", "asc")
                .queryParam("size", "100")
                .when()
                .get("/api/admin/tasks")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("content.title", String.class);

        List<String> mine = titles.stream().filter(t -> t.endsWith("-" + ts)).toList();
        assertEquals(List.of(sortA.getTitle(), sortB.getTitle(), sortC.getTitle()), mine);

        // Each task exposes the correct ownerUsername (maps title -> owner).
        List<Map<String, Object>> tasks = given()
                .header("Authorization", bearer(adminToken))
                .queryParam("sortBy", "title")
                .queryParam("direction", "asc")
                .queryParam("size", "100")
                .when()
                .get("/api/admin/tasks")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("content");

        for (Map<String, Object> task : tasks) {
            String title = (String) task.get("title");
            if (sortA.getTitle().equals(title) || sortC.getTitle().equals(title)) {
                assertEquals(ivan.getUsername(), task.get("ownerUsername"));
            } else if (sortB.getTitle().equals(title)) {
                assertEquals(judy.getUsername(), task.get("ownerUsername"));
            }
        }
    }

    @Test
    @DisplayName("Admin task filters reject invalid values")
    void adminTaskFiltersRejectInvalidValues() {
        String adminToken = createAdmin("strict_admin");

        given()
                .header("Authorization", bearer(adminToken))
                .queryParam("sortBy", "bogus")
                .when()
                .get("/api/admin/tasks")
                .then()
                .statusCode(400);

        given()
                .header("Authorization", bearer(adminToken))
                .queryParam("direction", "bogus")
                .when()
                .get("/api/admin/tasks")
                .then()
                .statusCode(400);

        given()
                .header("Authorization", bearer(adminToken))
                .queryParam("dueBefore", "notadate")
                .when()
                .get("/api/admin/tasks")
                .then()
                .statusCode(400);

        given()
                .header("Authorization", bearer(adminToken))
                .queryParam("priority", "bogus")
                .when()
                .get("/api/admin/tasks")
                .then()
                .statusCode(400);
    }

    private User saveUser(String baseUsername) {
        long timestamp = System.currentTimeMillis();
        return userRepository.save(User.builder()
                .username(baseUsername + "_" + timestamp)
                .password(passwordEncoder.encode("Pass1234!"))
                .email(baseUsername + "_" + timestamp + "@example.com")
                .role("USER")
                .build());
    }

    private Task saveTask(User owner, String title, Task.Priority priority, Task.Status status, LocalDate dueDate) {
        return taskRepository.save(Task.builder()
                .title(title)
                .priority(priority)
                .status(status)
                .dueDate(dueDate)
                .user(owner)
                .build());
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
