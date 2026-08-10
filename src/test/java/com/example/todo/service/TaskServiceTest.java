package com.example.todo.service;

import com.example.todo.entity.Task;
import com.example.todo.entity.User;
import com.example.todo.exception.ResourceNotFoundException;
import com.example.todo.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock TaskRepository taskRepository;

    @InjectMocks TaskService taskService;

    @Test
    void shouldThrowNotFoundWhenNotOwner() {
        User owner = User.builder().id(1L).build();
        User intruder = User.builder().id(2L).role("USER").build();
        Task task = Task.builder().id(1L).user(owner).build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThrows(ResourceNotFoundException.class, () ->
                taskService.getTaskById(1L, intruder));
        
        verify(taskRepository).findById(1L);
    }

    @Test
    void shouldThrowNotFoundWhenTaskMissing() {
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                taskService.getTaskById(999L, User.builder().id(1L).build()));
    }

    @Test
    void shouldCreateTaskWithDefaults() {
        User user = User.builder().id(1L).build();
        var request = com.example.todo.dto.task.TaskCreateRequest.builder()
                .title("Test")
                .build();

        when(taskRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = taskService.createTask(request, user);

        assertEquals("Test", response.getTitle());
        assertEquals(Task.Priority.MEDIUM, response.getPriority());
        assertEquals(Task.Status.TODO, response.getStatus());
        verify(taskRepository).save(any());
    }

    @Test
    void shouldUpdateTaskFields() {
        User user = User.builder().id(1L).build();
        Task task = Task.builder()
                .id(1L)
                .title("Old")
                .description("Old Desc")
                .priority(Task.Priority.LOW)
                .status(Task.Status.TODO)
                .user(user)
                .build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var request = com.example.todo.dto.task.TaskUpdateRequest.builder()
                .title("New")
                .status(Task.Status.DONE)
                .build();

        var response = taskService.updateTask(1L, request, user);

        assertEquals("New", response.getTitle());
        assertEquals("Old Desc", response.getDescription());
        assertEquals(Task.Priority.LOW, response.getPriority());
        assertEquals(Task.Status.DONE, response.getStatus());
    }

    @Test
    void shouldDeleteTaskWhenOwner() {
        User user = User.builder().id(1L).build();
        Task task = Task.builder().id(1L).user(user).build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        taskService.deleteTask(1L, user);

        verify(taskRepository).delete(task);
    }

    @Test
    void shouldThrowNotFoundWhenDeleteNotOwner() {
        User owner = User.builder().id(1L).build();
        User intruder = User.builder().id(2L).build();
        Task task = Task.builder().id(1L).user(owner).build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThrows(ResourceNotFoundException.class, () ->
                taskService.deleteTask(1L, intruder));
        verify(taskRepository, never()).delete(any());
    }
}
