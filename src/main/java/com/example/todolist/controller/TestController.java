package com.example.todolist.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/health")
    @Operation(summary = "Health check endpoint", description = "Simple health check to verify API is working")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "API is healthy")
    })
    public String health() {
        return "API is working!";
    }
}
