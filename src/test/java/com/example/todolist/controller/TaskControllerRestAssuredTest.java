package com.example.todolist.controller;

import com.example.todolist.util.RestAssuredTestBase;
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
import static org.hamcrest.Matchers.contains;
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
    void shouldValidateResponseTime() {        String taskBody = """
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

    @Test
    @DisplayName("Should filter tasks by status for the requesting user only")
    void shouldFilterTasksByStatus() {
        createTask(user1Token, "Todo One", "TODO");
        createTask(user1Token, "Todo Two", "TODO");
        createTask(user1Token, "Done One", "DONE");
        createTask(user2Token, "User2 Done", "DONE");

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("status", "DONE")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(1))
                .body("content[0].title", equalTo("Done One"))
                .body("content[0].status", equalTo("DONE"));

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("status", "TODO")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(2))
                .body("content[0].status", equalTo("TODO"))
                .body("content[1].status", equalTo("TODO"));
    }

    @Test
    @DisplayName("Should return all tasks when status param is omitted")
    void shouldReturnAllTasksWithoutStatusParam() {
        createTask(user1Token, "Task A", "TODO");
        createTask(user1Token, "Task B", "IN_PROGRESS");
        createTask(user1Token, "Task C", "DONE");

        given()
                .header("Authorization", bearer(user1Token))
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(3));
    }

    @Test
    @DisplayName("Should combine status filter with pagination")
    void shouldCombineStatusFilterWithPagination() {
        for (int i = 1; i <= 5; i++) {
            createTask(user1Token, "Todo " + i, "TODO");
        }
        createTask(user1Token, "Done One", "DONE");

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("status", "TODO")
                .queryParam("page", 0)
                .queryParam("size", 2)
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(2))
                .body("totalElements", equalTo(5))
                .body("totalPages", equalTo(3))
                .body("size", equalTo(2));

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("status", "TODO")
                .queryParam("page", 2)
                .queryParam("size", 2)
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(1))
                .body("totalElements", equalTo(5));
    }

    @Test
    @DisplayName("Should reject invalid status filter with 400")
    void shouldRejectInvalidStatusFilter() {
        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("status", "NOPE")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(400)
                .contentType("application/json")
                .body("$", hasKey("error"));
    }

    @Test
    @DisplayName("Should filter tasks by priority for the requesting user only")
    void shouldFilterTasksByPriority() {
        createTask(user1Token, "High One", "TODO", "HIGH");
        createTask(user1Token, "Low One", "TODO", "LOW");
        createTask(user1Token, "High Two", "DONE", "HIGH");
        createTask(user2Token, "User2 High", "DONE", "HIGH");

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("priority", "HIGH")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(2))
                .body("content.priority", hasItems("HIGH", "HIGH"))
                .body("content.title", hasItems("High One", "High Two"));
    }

    @Test
    @DisplayName("Should combine status and priority filters")
    void shouldCombineStatusAndPriorityFilters() {
        createTask(user1Token, "High Todo", "TODO", "HIGH");
        createTask(user1Token, "Low Todo", "TODO", "LOW");
        createTask(user1Token, "High Done", "DONE", "HIGH");

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("status", "TODO")
                .queryParam("priority", "HIGH")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(1))
                .body("content[0].title", equalTo("High Todo"))
                .body("content[0].status", equalTo("TODO"))
                .body("content[0].priority", equalTo("HIGH"));
    }

    @Test
    @DisplayName("Should reject invalid priority filter with 400")
    void shouldRejectInvalidPriorityFilter() {
        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("priority", "BOGUS")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(400)
                .contentType("application/json")
                .body("$", hasKey("error"));
    }

    @Test
    @DisplayName("Should return only overdue tasks, field/filter consistent and user-scoped")
    void shouldReturnOnlyOverdueTasks() {
        String pastDue = LocalDate.now().minusDays(1).toString();
        String futureDue = LocalDate.now().plusDays(1).toString();
        createTaskWithDueDate(user1Token, "Overdue Todo", "TODO", "HIGH", pastDue);
        createTaskWithDueDate(user1Token, "Future Task", "TODO", "LOW", futureDue);
        createTaskWithDueDate(user1Token, "Done Past Due", "DONE", "MEDIUM", pastDue);
        createTaskWithDueDate(user1Token, "No Date", "TODO", "MEDIUM", null);
        createTaskWithDueDate(user2Token, "User2 Overdue", "TODO", "HIGH", pastDue);

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("overdue", true)
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(1))
                .body("content[0].title", equalTo("Overdue Todo"))
                .body("content[0].overdue", equalTo(true));
    }

    @Test
    @DisplayName("Should return empty when overdue composes with DONE (contradiction)")
    void shouldReturnEmptyForOverdueAndDone() {
        String pastDue = LocalDate.now().minusDays(1).toString();
        createTaskWithDueDate(user1Token, "Overdue Todo", "TODO", "HIGH", pastDue);
        createTaskWithDueDate(user1Token, "Done Past Due", "DONE", "MEDIUM", pastDue);

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("overdue", true)
                .queryParam("status", "DONE")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", empty())
                .body("totalElements", equalTo(0));
    }

    @Test
    @DisplayName("Should compose overdue filter with priority")
    void shouldComposeOverdueWithPriority() {
        String pastDue = LocalDate.now().minusDays(1).toString();
        createTaskWithDueDate(user1Token, "Overdue High", "TODO", "HIGH", pastDue);
        createTaskWithDueDate(user1Token, "Overdue Low", "TODO", "LOW", pastDue);
        createTaskWithDueDate(user1Token, "Future High", "TODO", "HIGH", LocalDate.now().plusDays(1).toString());

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("overdue", true)
                .queryParam("priority", "HIGH")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(1))
                .body("content[0].title", equalTo("Overdue High"))
                .body("content[0].overdue", equalTo(true));
    }

    @Test
    @DisplayName("Should return all tasks when overdue param is absent or false")
    void shouldReturnAllWhenOverdueAbsentOrFalse() {
        String pastDue = LocalDate.now().minusDays(1).toString();
        createTaskWithDueDate(user1Token, "Overdue Todo", "TODO", "HIGH", pastDue);
        createTaskWithDueDate(user1Token, "Future Task", "TODO", "LOW", LocalDate.now().plusDays(1).toString());
        createTaskWithDueDate(user1Token, "No Date", "TODO", "MEDIUM", null);

        given()
                .header("Authorization", bearer(user1Token))
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(3));

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("overdue", false)
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(3));
    }

    @Test
    @DisplayName("Should mark past-due task as overdue")
    void shouldMarkPastDueTaskAsOverdue() {
        String pastDue = LocalDate.now().minusDays(1).toString();
        String taskBody = String.format("""
            {
                "title": "Past Due Task",
                "dueDate": "%s"
            }
            """, pastDue);

        given()
                .header("Authorization", bearer(user1Token))
                .body(taskBody)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(201)
                .contentType("application/json")
                .body("dueDate", equalTo(pastDue))
                .body("overdue", equalTo(true));
    }

    @Test
    @DisplayName("Should not mark future-dated task as overdue")
    void shouldNotMarkFutureDatedTaskAsOverdue() {
        String futureDue = LocalDate.now().plusDays(1).toString();
        String taskBody = String.format("""
            {
                "title": "Future Task",
                "dueDate": "%s"
            }
            """, futureDue);

        given()
                .header("Authorization", bearer(user1Token))
                .body(taskBody)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(201)
                .contentType("application/json")
                .body("dueDate", equalTo(futureDue))
                .body("overdue", equalTo(false));
    }

    @Test
    @DisplayName("Should not mark past-due DONE task as overdue")
    void shouldNotMarkPastDueDoneTaskAsOverdue() {
        String pastDue = LocalDate.now().minusDays(1).toString();
        String taskBody = String.format("""
            {
                "title": "Done Past Due Task",
                "status": "DONE",
                "dueDate": "%s"
            }
            """, pastDue);

        given()
                .header("Authorization", bearer(user1Token))
                .body(taskBody)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(201)
                .contentType("application/json")
                .body("dueDate", equalTo(pastDue))
                .body("overdue", equalTo(false));
    }

    @Test
    @DisplayName("Should filter by dueBefore inclusive and stay user-scoped")
    void shouldFilterByDueBeforeInclusive() {
        String now = LocalDate.now().toString();
        createTaskWithDueDate(user1Token, "Past Task", "TODO", "HIGH", LocalDate.now().minusDays(5).toString());
        createTaskWithDueDate(user1Token, "Today Task", "TODO", "HIGH", now);
        createTaskWithDueDate(user1Token, "Future Task", "TODO", "HIGH", LocalDate.now().plusDays(5).toString());
        createTaskWithDueDate(user1Token, "No Date Task", "TODO", "HIGH", null);
        createTaskWithDueDate(user2Token, "User2 Past", "TODO", "HIGH", LocalDate.now().minusDays(5).toString());

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("dueBefore", now)
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(2))
                .body("content.title", hasItems("Past Task", "Today Task"))
                .body("content.title", not(hasItems("Future Task", "No Date Task", "User2 Past")));
    }

    @Test
    @DisplayName("Should filter by dueAfter inclusive and stay user-scoped")
    void shouldFilterByDueAfterInclusive() {
        String now = LocalDate.now().toString();
        createTaskWithDueDate(user1Token, "Past Task", "TODO", "HIGH", LocalDate.now().minusDays(5).toString());
        createTaskWithDueDate(user1Token, "Today Task", "TODO", "HIGH", now);
        createTaskWithDueDate(user1Token, "Future Task", "TODO", "HIGH", LocalDate.now().plusDays(5).toString());
        createTaskWithDueDate(user1Token, "No Date Task", "TODO", "HIGH", null);
        createTaskWithDueDate(user2Token, "User2 Future", "TODO", "HIGH", LocalDate.now().plusDays(5).toString());

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("dueAfter", now)
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(2))
                .body("content.title", hasItems("Today Task", "Future Task"))
                .body("content.title", not(hasItems("Past Task", "No Date Task", "User2 Future")));
    }

    @Test
    @DisplayName("Should return only tasks due exactly on the single-day range")
    void shouldReturnTasksDueExactlyOnSingleDayRange() {
        String now = LocalDate.now().toString();
        createTaskWithDueDate(user1Token, "Past Task", "TODO", "HIGH", LocalDate.now().minusDays(5).toString());
        createTaskWithDueDate(user1Token, "Today Task", "TODO", "HIGH", now);
        createTaskWithDueDate(user1Token, "Future Task", "TODO", "HIGH", LocalDate.now().plusDays(5).toString());
        createTaskWithDueDate(user1Token, "No Date Task", "TODO", "HIGH", null);

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("dueBefore", now)
                .queryParam("dueAfter", now)
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(1))
                .body("content[0].title", equalTo("Today Task"));
    }

    @Test
    @DisplayName("Should return empty page for inverted date range")
    void shouldReturnEmptyForInvertedRange() {
        String now = LocalDate.now().toString();
        createTaskWithDueDate(user1Token, "Past Task", "TODO", "HIGH", LocalDate.now().minusDays(5).toString());
        createTaskWithDueDate(user1Token, "Future Task", "TODO", "HIGH", LocalDate.now().plusDays(5).toString());

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("dueBefore", now)
                .queryParam("dueAfter", LocalDate.now().plusDays(10).toString())
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", empty())
                .body("totalElements", equalTo(0));
    }

    @Test
    @DisplayName("Should exclude tasks without a due date whenever a date filter is active")
    void shouldExcludeNullDueDateWhenDateFilterActive() {
        String now = LocalDate.now().toString();
        createTaskWithDueDate(user1Token, "Dated Task", "TODO", "HIGH", now);
        createTaskWithDueDate(user1Token, "No Date Task", "TODO", "HIGH", null);

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("dueBefore", now)
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(1))
                .body("content[0].title", equalTo("Dated Task"));

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("dueAfter", now)
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(1))
                .body("content[0].title", equalTo("Dated Task"));
    }

    @Test
    @DisplayName("Should compose date filter with status and priority")
    void shouldComposeDateFilterWithStatusAndPriority() {
        String now = LocalDate.now().toString();
        createTaskWithDueDate(user1Token, "High Todo Past", "TODO", "HIGH", LocalDate.now().minusDays(5).toString());
        createTaskWithDueDate(user1Token, "Low Todo Past", "TODO", "LOW", LocalDate.now().minusDays(5).toString());
        createTaskWithDueDate(user1Token, "High Done Past", "DONE", "HIGH", LocalDate.now().minusDays(5).toString());

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("dueBefore", now)
                .queryParam("status", "TODO")
                .queryParam("priority", "HIGH")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(1))
                .body("content[0].title", equalTo("High Todo Past"));
    }

    @Test
    @DisplayName("Should compose date filter with overdue")
    void shouldComposeDateFilterWithOverdue() {
        String now = LocalDate.now().toString();
        createTaskWithDueDate(user1Token, "Overdue Past", "TODO", "HIGH", LocalDate.now().minusDays(5).toString());
        createTaskWithDueDate(user1Token, "Due Today", "TODO", "HIGH", now);
        createTaskWithDueDate(user1Token, "Future Task", "TODO", "HIGH", LocalDate.now().plusDays(5).toString());

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("dueBefore", now)
                .queryParam("overdue", true)
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(1))
                .body("content[0].title", equalTo("Overdue Past"))
                .body("content[0].overdue", equalTo(true));
    }

    @Test
    @DisplayName("Should reject malformed date filter with 400")
    void shouldRejectMalformedDateFilter() {
        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("dueBefore", "notadate")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(400)
                .contentType("application/json")
                .body("$", hasKey("error"));

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("dueAfter", "13/13/2026")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(400)
                .contentType("application/json")
                .body("$", hasKey("error"));
    }

    @Test
    @DisplayName("Should return all tasks when date params are omitted")
    void shouldReturnAllWhenDateParamsOmitted() {
        createTaskWithDueDate(user1Token, "Past Task", "TODO", "HIGH", LocalDate.now().minusDays(5).toString());
        createTaskWithDueDate(user1Token, "Future Task", "TODO", "HIGH", LocalDate.now().plusDays(5).toString());
        createTaskWithDueDate(user1Token, "No Date Task", "TODO", "HIGH", null);

        given()
                .header("Authorization", bearer(user1Token))
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(3));
    }

    @Test
    @DisplayName("Should sort tasks by title ascending")
    void shouldSortByTitleAscending() {
        createTaskWithDueDate(user1Token, "C_Task", "DONE", "MEDIUM", null);
        createTaskWithDueDate(user1Token, "A_Task", "TODO", "LOW", LocalDate.now().plusDays(5).toString());
        createTaskWithDueDate(user1Token, "B_Task", "TODO", "HIGH", LocalDate.now().minusDays(5).toString());

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("sortBy", "title")
                .queryParam("direction", "asc")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content.title", contains("A_Task", "B_Task", "C_Task"));
    }

    @Test
    @DisplayName("Should sort tasks by priority descending")
    void shouldSortByPriorityDescending() {
        createTaskWithDueDate(user1Token, "Alpha", "TODO", "HIGH", null);
        createTaskWithDueDate(user1Token, "Beta", "TODO", "LOW", null);
        createTaskWithDueDate(user1Token, "Gamma", "TODO", "MEDIUM", null);

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("sortBy", "priority")
                .queryParam("direction", "desc")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content.title", contains("Gamma", "Beta", "Alpha"));
    }

    @Test
    @DisplayName("Should sort tasks by due date ascending and descending")
    void shouldSortByDueDateBothDirections() {
        createTaskWithDueDate(user1Token, "Past Task", "TODO", "LOW", LocalDate.now().minusDays(5).toString());
        createTaskWithDueDate(user1Token, "Today Task", "TODO", "LOW", LocalDate.now().toString());
        createTaskWithDueDate(user1Token, "Future Task", "TODO", "LOW", LocalDate.now().plusDays(5).toString());

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("sortBy", "dueDate")
                .queryParam("direction", "asc")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content.title", contains("Past Task", "Today Task", "Future Task"));

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("sortBy", "dueDate")
                .queryParam("direction", "desc")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content.title", contains("Future Task", "Today Task", "Past Task"));
    }

    @Test
    @DisplayName("Should include tasks without a due date when sorting by due date")
    void shouldIncludeNullDueDateWhenSortingByDueDate() {
        createTaskWithDueDate(user1Token, "Dated", "TODO", "LOW", LocalDate.now().toString());
        createTaskWithDueDate(user1Token, "No Date", "TODO", "LOW", null);

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("sortBy", "dueDate")
                .queryParam("direction", "asc")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(2))
                .body("content.title", hasItems("Dated", "No Date"));
    }

    @Test
    @DisplayName("Should reject invalid sortBy with 400")
    void shouldRejectInvalidSortBy() {
        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("sortBy", "bogus")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(400)
                .contentType("application/json")
                .body("$", hasKey("error"));
    }

    @Test
    @DisplayName("Should reject invalid direction with 400")
    void shouldRejectInvalidDirection() {
        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("direction", "sideways")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(400)
                .contentType("application/json")
                .body("$", hasKey("error"));
    }

    @Test
    @DisplayName("Should compose sort with an active filter")
    void shouldComposeSortWithFilter() {
        createTaskWithDueDate(user1Token, "High Done C", "DONE", "HIGH", null);
        createTaskWithDueDate(user1Token, "Low Todo B", "TODO", "LOW", null);
        createTaskWithDueDate(user1Token, "High Todo A", "TODO", "HIGH", null);

        given()
                .header("Authorization", bearer(user1Token))
                .queryParam("status", "TODO")
                .queryParam("sortBy", "priority")
                .queryParam("direction", "asc")
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("content", hasSize(2))
                .body("content.title", contains("High Todo A", "Low Todo B"));
    }

    private void createTask(String token, String title, String status) {
        createTask(token, title, status, "LOW");
    }

    private void createTask(String token, String title, String status, String priority) {
        given()
                .header("Authorization", bearer(token))
                .body(String.format("""
                    {
                        "title": "%s",
                        "priority": "%s",
                        "status": "%s"
                    }
                    """, title, priority, status))
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(201);
    }

    private void createTaskWithDueDate(String token, String title, String status, String priority, String dueDate) {
        String body;
        if (dueDate == null) {
            body = String.format("""
                {
                    "title": "%s",
                    "priority": "%s",
                    "status": "%s"
                }
                """, title, priority, status);
        } else {
            body = String.format("""
                {
                    "title": "%s",
                    "priority": "%s",
                    "status": "%s",
                    "dueDate": "%s"
                }
                """, title, priority, status, dueDate);
        }
        given()
                .header("Authorization", bearer(token))
                .body(body)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(201);
    }
}
