package com.example.todolist.controller;

import com.example.todolist.dto.task.PaginatedTaskResponse;
import com.example.todolist.dto.task.TaskCreateRequest;
import com.example.todolist.dto.task.TaskResponse;
import com.example.todolist.dto.task.TaskUpdateRequest;
import com.example.todolist.entity.Task;
import com.example.todolist.entity.User;
import com.example.todolist.exception.UserNotAuthenticatedException;
import com.example.todolist.security.CurrentUserService;
import com.example.todolist.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final CurrentUserService currentUserService;

    private User getCurrentUser() {
        return currentUserService.getCurrentUser()
                .orElseThrow(() -> new UserNotAuthenticatedException("User not authenticated"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createTask(@Valid @RequestBody TaskCreateRequest request) {
        return taskService.createTask(request, getCurrentUser());
    }

    @GetMapping("/{id}")
    public TaskResponse getTask(@PathVariable Long id) {
        return taskService.getTaskById(id, getCurrentUser());
    }

    @GetMapping
    public PaginatedTaskResponse getTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Task.Status status,
            @RequestParam(required = false) Task.Priority priority,
            @RequestParam(required = false) Boolean overdue) {
        int normalizedSize = Math.max(1, Math.min(size, 100));
        return taskService.getTasksPaginated(getCurrentUser(), page, normalizedSize, status, priority, overdue);
    }

    @PatchMapping("/{id}")
    public TaskResponse updateTask(@PathVariable Long id, @Valid @RequestBody TaskUpdateRequest request) {
        return taskService.updateTask(id, request, getCurrentUser());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id, getCurrentUser());
    }
}