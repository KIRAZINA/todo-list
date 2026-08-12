package com.example.todolist.util;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base class for RestAssured API tests.
 * Provides common configuration and setup for all API integration tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class RestAssuredTestBase {

    @LocalServerPort
    protected int port;

    @Autowired
    private Environment environment;

    @Autowired
    private ServletWebServerApplicationContext webServerApplicationContext;

    /**
     * Setup for each test method.
     * Configures the base URI and port for the test server.
     */
    @BeforeEach
    public void setup() {
        RestAssured.baseURI = "http://localhost";
        int resolvedPort = 0;
        Integer webServerPort = null;
        if (webServerApplicationContext != null && webServerApplicationContext.getWebServer() != null) {
            webServerPort = webServerApplicationContext.getWebServer().getPort();
            resolvedPort = webServerPort;
        }
        Integer envPort = null;
        if (resolvedPort <= 0) {
            envPort = environment.getProperty("local.server.port", Integer.class);
            if (envPort != null && envPort > 0) {
                resolvedPort = envPort;
            }
        }
        if (resolvedPort <= 0 && port > 0) {
            resolvedPort = port;
        }
        if (resolvedPort <= 0) {
            throw new IllegalStateException("Local test server port not available");
        }
        RestAssured.port = resolvedPort;
        RequestSpecBuilder requestSpecBuilder = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .addFilter(new RequestLoggingFilter())
                .addFilter(new ResponseLoggingFilter())
                .setRelaxedHTTPSValidation()
                .setPort(resolvedPort);
        RestAssured.requestSpecification = requestSpecBuilder.build();
    }

    /**
     * Helper method to create a bearer token for authentication.
     * 
     * @param token the JWT token
     * @return the bearer token string in format "Bearer {token}"
     */
    protected String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * Helper method to extract token from login response.
     * 
     * @param loginResponse the response body from login endpoint
     * @return the JWT token
     */
    protected String extractToken(String loginResponse) {
        return io.restassured.path.json.JsonPath.from(loginResponse).getString("token");
    }

    /**
     * Helper method to register a test user and return login token.
     * Uses timestamp to ensure unique usernames.
     * 
     * @param username the base username for the test user
     * @param password the password for the test user
     * @param email the email for the test user
     * @return the JWT token for authentication
     */
    protected String registerAndLogin(String username, String password, String email) {
        long timestamp = System.currentTimeMillis();
        String uniqueUsername = username + "_" + timestamp;
        String uniqueEmail = email.replace("@", "_" + timestamp + "@");
        
        // Register user
        io.restassured.response.Response registerResponse = io.restassured.RestAssured.given()
                .body(String.format("""
                    {
                        "username": "%s",
                        "password": "%s",
                        "email": "%s"
                    }
                    """, uniqueUsername, password, uniqueEmail))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .extract()
                .response();

        // Login and get token
        io.restassured.response.Response loginResponse = io.restassured.RestAssured.given()
                .body(String.format("""
                    {
                        "username": "%s",
                        "password": "%s"
                    }
                    """, uniqueUsername, password))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .response();

        return extractToken(loginResponse.asString());
    }
}
