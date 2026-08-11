package com.example.todo.util;

import com.example.todo.dto.user.UserLoginRequest;
import com.example.todo.dto.user.UserRegisterRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected String registerAndLogin(String username, String password, String email) throws Exception {
        UserRegisterRequest registerRequest = UserRegisterRequest.builder()
                .username(username)
                .password(password)
                .email(email)
                .build();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));

        UserLoginRequest loginRequest = UserLoginRequest.builder()
                .username(username)
                .password(password)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        return extractToken(loginResult);
    }

    protected String extractToken(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("token").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected String registerDefaultUser() throws Exception {
        return registerAndLogin("testuser", "Password123", "test@example.com");
    }

    protected String registerAdminUser() throws Exception {
        return registerAndLogin("admin", "Adminpass123", "admin@example.com");
    }

    protected String[] registerMultipleUsers(int count) throws Exception {
        String[] tokens = new String[count];
        for (int i = 0; i < count; i++) {
            tokens[i] = registerAndLogin(
                    "user" + (i + 1),
                    "Password123",
                    "user" + (i + 1) + "@example.com"
            );
        }
        return tokens;
    }
}
