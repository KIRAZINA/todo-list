package com.example.todo.controller;

import com.example.todo.util.RestAssuredTestBase;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.lessThan;

/**
 * RestAssured integration tests for TaskController API endpoints.
 * Tests CRUD operations for tasks with authentication and authorization.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TaskControllerRestAssuredTest extends RestAssuredTestBase {

    private String user1Token;
    private String user2Token;

    @BeforeEach
    void setUp() {
        user1Token = registerAndLogin("user1", "Password123", "user1@example.com");
        user2Token = registerAndLogin("user2", "Password123", "user2@example.com");
    }

    @Test
    @DisplayName("Should create and retrieve own task successfully")
    void shouldCreateAndGetOwnTask() {
        String createTaskBody = String.format("""
            {
                "title": "Learn Spring Boot",
                "description": "Complete backend project",
                "priority": "HIGH",
                "status": "TODO",
                "dueDate": "%s"
            }
            """, LocalDate.of(2026, 12, 31));

        // Create task
        Response createResponse = given()
                .header("Authorization", bearer(user1Token))
                .body(createTaskBody)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(201)
                .contentType("application/json")
                .body("title", equalTo("Learn Spring Boot"))
                .body("description", equalTo("Complete backend project"))
                .body("priority", equalTo("HIGH"))
                .body("status", equalTo("TODO"))
                .body("dueDate", equalTo("2026-12-31"))
                .body("$", hasKey("id"))
                .body("$", hasKey("createdAt"))
                .extract()
                .response();

        Long taskId = createResponse.jsonPath().getLong("id");

        // Get task by ID
        given()
                .header("Authorization", bearer(user1Token))
                .pathParam("id", taskId)
                .when()
                .get("/api/tasks/{id}")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("id", equalTo(taskId.intValue()))
                .body("title", equalTo("Learn Spring Boot"));
    }

    @Test
    @DisplayName("Should return 404 when trying to access another user's task")
    void shouldReturnNotFoundForForeignTask() {
        String createTaskBody = """
            {
                "title": "Secret Task",
                "priority": "LOW",
                "status": "TODO"
            }
            """;

        // Create task with user1
        Response createResponse = given()
                .header("Authorization", bearer(user1Token))
                .body(createTaskBody)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(201)
                .extract()
                .response();

        Long taskId = createResponse.jsonPath().getLong("id");

        // Try to access with user2 - should return 404
        given()
                .header("Authorization", bearer(user2Token))
                .pathParam("id", taskId)
                .when()
                .get("/api/tasks/{id}")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("Should update and delete own task successfully")
    void shouldUpdateAndDeleteOwnTask() {
        String createTaskBody = """
            {
                "title": "Task to Update",
                "priority": "MEDIUM",
                "status": "IN_PROGRESS"
            }
            """;

        // Create task
        Response createResponse = given()
                .header("Authorization", bearer(user1Token))
                .body(createTaskBody)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(201)
                .extract()
                .response();

        Long taskId = createResponse.jsonPath().getLong("id");

        // Update task
        String updateTaskBody = """
            {
                "title": "Updated Successfully",
                "description": "Task has been updated",
                "status": "DONE"
            }
            """;

         given()
                .header("Authorization", bearer(user1Token))
                .pathParam("id", taskId)
                .body(updateTaskBody)
                .when()
                .patch("/api/tasks/{id}")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("title", equalTo("Updated Successfully"))
                .body("description", equalTo("Task has been updated"))
                .body("status", equalTo("DONE"))
                .body("priority", equalTo("MEDIUM")); // Original priority should remain

        // Delete task
        given()
                .header("Authorization", bearer(user1Token))
                .pathParam("id", taskId)
                .when()
                .delete("/api/tasks/{id}")
                .then()
                .statusCode(204);

        // Verify task is deleted
        given()
                .header("Authorization", bearer(user1Token))
                .pathParam("id", taskId)
                .when()
                .get("/api/tasks/{id}")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("Should get list of tasks")
    void shouldGetAllTasks() {
        // Create multiple tasks
        for (int i = 1; i <= 5; i++) {
            String taskBody = String.format("""
                {
                    "title": "Task %d",
                    "priority": "LOW",
                    "status": "TODO"
                }
                """, i);

            given()
                    .header("Authorization", bearer(user1Token))
                    .body(taskBody)
                    .when()
                    .post("/api/tasks")
                    .then()
                    .statusCode(201);
        }

        // Get all tasks
        given()
                .header("Authorization", bearer(user1Token))
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(5));
    }

    @Test
    @DisplayName("Should prevent deletion of another user's task")
    void shouldNotAllowDeleteForeignTask() {
        String createTaskBody = """
            {
                "title": "User1 Private Task",
                "priority": "HIGH",
                "status": "TODO"
            }
            """;

        // Create task with user1
        Response createResponse = given()
                .header("Authorization", bearer(user1Token))
                .body(createTaskBody)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(201)
                .extract()
                .response();

        Long taskId = createResponse.jsonPath().getLong("id");

        // User2 tries to delete user1's task - should return 404
        given()
                .header("Authorization", bearer(user2Token))
                .pathParam("id", taskId)
                .when()
                .delete("/api/tasks/{id}")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("Should reject unauthenticated access to task endpoints")
    void shouldRejectUnauthenticatedAccess() {
        given()
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(401);

        given()
                .body("""
                    {
                        "title": "Unauthorized Task",
                        "priority": "LOW",
                        "status": "TODO"
                    }
                    """)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("Should return empty list for new user")
    void shouldReturnEmptyListForNewUser() {
        given()
                .header("Authorization", bearer(user1Token))
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", empty());
    }

    @Test
    @DisplayName("Should update only specified fields in task")
    void shouldUpdateOnlyOwnFields() {
        String createTaskBody = """
            {
                "title": "Original Title",
                "description": "Original Description",
                "priority": "LOW",
                "status": "TODO"
            }
            """;

        // Create task
        Response createResponse = given()
                .header("Authorization", bearer(user1Token))
                .body(createTaskBody)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(201)
                .extract()
                .response();

        Long taskId = createResponse.jsonPath().getLong("id");

        // Update only title
        String updateBody = """
            {
                "title": "New Title Only"
            }
            """;

         given()
                .header("Authorization", bearer(user1Token))
                .pathParam("id", taskId)
                .body(updateBody)
                .when()
                .patch("/api/tasks/{id}")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("title", equalTo("New Title Only"))
                .body("description", equalTo("Original Description")) // Should remain unchanged
                .body("priority", equalTo("LOW")) // Should remain unchanged
                .body("status", equalTo("TODO")); // Should remain unchanged
    }

    @ParameterizedTest
    @CsvSource({
            "HIGH, TODO",
            "MEDIUM, IN_PROGRESS", 
            "LOW, DONE"
    })
    @DisplayName("Should create tasks with different priorities and statuses")
    void shouldCreateTasksWithDifferentPriorityAndStatus(String priority, String status) {
        String taskBody = String.format("""
            {
                "title": "Test Task",
                "priority": "%s",
                "status": "%s"
            }
            """, priority, status);

        given()
                .header("Authorization", bearer(user1Token))
                .body(taskBody)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(201)
                .contentType("application/json")
                .body("priority", equalTo(priority))
                .body("status", equalTo(status));
    }

    @Test
    @DisplayName("Should validate invalid task creation")
    void shouldValidateInvalidTaskCreation() {
        // Test with empty title
        given()
                .header("Authorization", bearer(user1Token))
                .body("""
                    {
                        "title": "",
                        "priority": "LOW",
                        "status": "TODO"
                    }
                    """)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(400);

        // Test with invalid priority (should fail validation)
        given()
                .header("Authorization", bearer(user1Token))
                .body("""
                    {
                        "title": "Valid Title",
                        "priority": "INVALID_PRIORITY",
                        "status": "TODO"
                    }
                    """)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(400);

        // Test with invalid status (should fail validation)
        given()
                .header("Authorization", bearer(user1Token))
                .body("""
                    {
                        "title": "Valid Title",
                        "priority": "HIGH",
                        "status": "INVALID_STATUS"
                    }
                    """)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("Should handle non-existent task ID gracefully")
    void shouldHandleNonExistentTaskId() {
        given()
                .header("Authorization", bearer(user1Token))
                .pathParam("id", 99999L)
                .when()
                .get("/api/tasks/{id}")
                .then()
                .statusCode(404);

        given()
                .header("Authorization", bearer(user1Token))
                .pathParam("id", 99999L)
                .body("""
                    {
                        "title": "Updated Task"
                    }
                    """)
                .when()
                .patch("/api/tasks/{id}")
                .then()
                .statusCode(404);

        given()
                .header("Authorization", bearer(user1Token))
                .pathParam("id", 99999L)
                .when()
                .delete("/api/tasks/{id}")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("Should validate response time for task operations")
    void shouldValidateResponseTime() {
        String taskBody = """
            {
                "title": "Performance Test Task",
                "priority": "MEDIUM",
                "status": "TODO"
            }
            """;

        // Task creation should be fast
        given()
                .header("Authorization", bearer(user1Token))
                .body(taskBody)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(201)
                .time(lessThan(1000L)); // Less than 1 second

        // Task retrieval should be fast
        given()
                .header("Authorization", bearer(user1Token))
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .time(lessThan(500L)); // Less than 500ms
    }
}
