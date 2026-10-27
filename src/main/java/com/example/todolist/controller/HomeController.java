package com.example.todolist.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of(
                "message", "Welcome to Todo List Backend API",
                "version", "1.0",
                "auth", "/api/v1/auth/login or /api/v1/auth/register",
                "tasks", "/api/v1/tasks (requires authentication)"
        );
    }
}