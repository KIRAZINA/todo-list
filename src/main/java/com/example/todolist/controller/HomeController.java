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
                "auth", "/api/auth/login or /api/auth/register",
                "tasks", "/api/tasks (requires authentication)"
        );
    }
}