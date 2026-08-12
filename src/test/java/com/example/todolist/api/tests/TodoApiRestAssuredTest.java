package com.example.todolist.api.tests;

import com.example.todolist.util.RestAssuredTestBase;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;

/**
 * API tests for the Todo application.
 * Tests authentication, authorization, validation, and CRUD operations.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TodoApiRestAssuredTest extends RestAssuredTestBase {

    @Test
    @DisplayName("Should validate complete user authentication flow")
    void shouldValidateCompleteAuthenticationFlow() {
        long timestamp = System.currentTimeMillis();
        // Register user
        Map<String, Object> registerRequest = new HashMap<>();
        registerRequest.put("username", "flow_test_user_" + timestamp);
        registerRequest.put("password", "SecureFlow123!");
        registerRequest.put("email", "flow_" + timestamp + "@example.com");

        given()
                .contentType(ContentType.JSON)
                .body(registerRequest)
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("username", equalTo("flow_test_user_" + timestamp))
                .body("email", equalTo("flow_" + timestamp + "@example.com"))
                .body("$", hasKey("id"))
                .body("$", hasKey("createdAt"));

        // Login
        Map<String, Object> loginRequest = new HashMap<>();
        loginRequest.put("username", "flow_test_user_" + timestamp);
        loginRequest.put("password", "SecureFlow123!");

        Response response = given()
                .contentType(ContentType.JSON)
                .body(loginRequest)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("token", notNullValue())
                .body("type", equalTo("Bearer"))
                .extract()
                .response();

        // Verify JWT token structure
        String token = response.jsonPath().getString("token");
        String[] tokenParts = token.split("\\.");
        assertEquals(3, tokenParts.length);

        // Use token to access protected endpoint
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("content", hasSize(0));
    }

    @Test
    @DisplayName("Should handle task CRUD operations with full validation")
    void shouldHandleTaskCrudOperations() {
        long timestamp = System.currentTimeMillis();
        String token = registerAndLogin("crud_user_" + timestamp, "CrudPass123!", "crud_" + timestamp + "@example.com");

        // Create task
        Response createResponse = given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "title": "Complete API Testing",
                        "description": "Write comprehensive API tests",
                        "priority": "HIGH",
                        "status": "IN_PROGRESS",
                        "dueDate": "2026-12-31"
                    }
                    """)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("title", equalTo("Complete API Testing"))
                .body("priority", equalTo("HIGH"))
                .body("status", equalTo("IN_PROGRESS"))
                .extract()
                .response();

        Integer taskId = createResponse.jsonPath().getInt("id");

        // Get task
        given()
                .header("Authorization", bearer(token))
                .pathParam("id", taskId)
                .when()
                .get("/api/tasks/{id}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("title", equalTo("Complete API Testing"))
                .body("id", equalTo(taskId));

        // Update task
        given()
                .header("Authorization", bearer(token))
                .pathParam("id", taskId)
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "title": "Complete API Testing - Updated",
                        "status": "DONE"
                    }
                    """)
                .when()
                .patch("/api/tasks/{id}")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("title", equalTo("Complete API Testing - Updated"))
                .body("status", equalTo("DONE"));

        // Delete task
        given()
                .header("Authorization", bearer(token))
                .pathParam("id", taskId)
                .when()
                .delete("/api/tasks/{id}")
                .then()
                .statusCode(204);

        // Verify deletion
        given()
                .header("Authorization", bearer(token))
                .pathParam("id", taskId)
                .when()
                .get("/api/tasks/{id}")
                .then()
                .statusCode(404);
    }

    @ParameterizedTest
    @DisplayName("Should create tasks with different priorities")
    @ValueSource(strings = {"LOW", "MEDIUM", "HIGH"})
    void shouldCreateTasksWithDifferentPriorities(String priority) {
        long timestamp = System.currentTimeMillis();
        String token = registerAndLogin("priority_user_" + timestamp, "PriorityPass123!", "priority_" + timestamp + "@example.com");

        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body(String.format("""
                    {
                        "title": "Task with %s Priority",
                        "priority": "%s",
                        "status": "TODO"
                    }
                    """, priority, priority))
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("priority", equalTo(priority))
                .body("title", containsString(priority));
    }

    @Test
    @DisplayName("Should validate API response structure")
    void shouldValidateApiResponseStructure() {
        long timestamp = System.currentTimeMillis();
        String token = registerAndLogin("structure_user_" + timestamp, "StructurePass123!", "structure_" + timestamp + "@example.com");

        // Validate user response structure
        given()
                .contentType(ContentType.JSON)
                .body(String.format("""
                    {
                        "username": "structure_validation_user_%d",
                        "password": "StructurePass123!",
                        "email": "structure_%d@example.com"
                    }
                    """, timestamp, timestamp))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("username", notNullValue())
                .body("email", notNullValue())
                .body("id", notNullValue())
                .body("role", notNullValue())
                .body("createdAt", notNullValue());

        // Validate task response structure
        given()
                .header("Authorization", bearer(token))
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "title": "Structure Validation Task",
                        "description": "Task for structure validation",
                        "priority": "HIGH",
                        "status": "TODO"
                    }
                    """)
                .when()
                .post("/api/tasks")
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("id", notNullValue())
                .body("title", notNullValue())
                .body("description", notNullValue())
                .body("priority", notNullValue())
                .body("status", notNullValue())
                .body("userId", notNullValue())
                .body("createdAt", notNullValue())
                .body("updatedAt", notNullValue());
    }

    @Test
    @DisplayName("Should handle multiple tasks")
    void shouldHandleMultipleTasks() {
        long timestamp = System.currentTimeMillis();
        String token = registerAndLogin("multi_user_" + timestamp, "MultiPass123!", "multi_" + timestamp + "@example.com");

        // Create multiple tasks
        for (int i = 1; i <= 5; i++) {
            given()
                    .header("Authorization", bearer(token))
                    .contentType(ContentType.JSON)
                    .body(String.format("""
                        {
                            "title": "Task %02d",
                            "priority": "MEDIUM",
                            "status": "TODO"
                        }
                        """, i))
                    .when()
                    .post("/api/tasks")
                    .then()
                    .statusCode(201);
        }

        // Verify all tasks are returned
        given()
                .header("Authorization", bearer(token))
                .when()
                .get("/api/tasks")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("content", hasSize(5));
    }

    @Test
    @DisplayName("Should handle error responses")
    void shouldHandleErrorResponses() {
        long timestamp = System.currentTimeMillis();
        
        // Test validation errors
        given()
                .contentType(ContentType.JSON)
                .body("""
                    {
                        "username": "",
                        "password": "",
                        "email": "invalid-email"
                    }
                    """)
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(400)
                .contentType(ContentType.JSON);

        // Test not found errors
        String token = registerAndLogin("error_user_" + timestamp, "ErrorPass123!", "error_" + timestamp + "@example.com");
        
        given()
                .header("Authorization", bearer(token))
                .pathParam("id", 99999)
                .when()
                .get("/api/tasks/{id}")
                .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", notNullValue())
                .body("error", equalTo("Task not found"));

        // Test unauthorized access
        given()
                .pathParam("id", 1)
                .when()
                .get("/api/tasks/{id}")
                .then()
                .statusCode(401);
    }
}

