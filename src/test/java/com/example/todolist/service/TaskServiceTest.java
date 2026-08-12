package com.example.todolist.service;

import com.example.todolist.entity.Task;
import com.example.todolist.entity.User;
import com.example.todolist.exception.ResourceNotFoundException;
import com.example.todolist.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
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
        var request = com.example.todolist.dto.task.TaskCreateRequest.builder()
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

        var request = com.example.todolist.dto.task.TaskUpdateRequest.builder()
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
        verify(taskRepository, never()).delete(any(Task.class));
    }

    @Test
    void shouldUseFindByUserWhenNoFilters() {
        User user = User.builder().id(1L).build();
        Page<Task> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(taskRepository.findByUser(eq(user), any(Pageable.class))).thenReturn(emptyPage);

        taskService.getTasksPaginated(user, 0, 20, null, null, null);

        verify(taskRepository).findByUser(eq(user), any(Pageable.class));
        verify(taskRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldUseFindByUserWhenOverdueFalseWithNoOtherFilters() {
        User user = User.builder().id(1L).build();
        Page<Task> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(taskRepository.findByUser(eq(user), any(Pageable.class))).thenReturn(emptyPage);

        taskService.getTasksPaginated(user, 0, 20, null, null, Boolean.FALSE);

        verify(taskRepository).findByUser(eq(user), any(Pageable.class));
        verify(taskRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldUseSpecificationWhenOverdueTrue() {
        User user = User.builder().id(1L).build();
        Page<Task> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        taskService.getTasksPaginated(user, 0, 20, null, null, Boolean.TRUE);

        verify(taskRepository).findAll(any(Specification.class), any(Pageable.class));
        verify(taskRepository, never()).findByUser(any(), any());
    }

    @Test
    void shouldUseSpecificationWhenStatusProvided() {
        User user = User.builder().id(1L).build();
        Page<Task> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        taskService.getTasksPaginated(user, 0, 20, Task.Status.DONE, null, null);

        verify(taskRepository).findAll(any(Specification.class), any(Pageable.class));
        verify(taskRepository, never()).findByUser(any(), any());
    }

    @Test
    void shouldUseSpecificationWhenPriorityProvided() {
        User user = User.builder().id(1L).build();
        Page<Task> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        taskService.getTasksPaginated(user, 0, 20, null, Task.Priority.HIGH, null);

        verify(taskRepository).findAll(any(Specification.class), any(Pageable.class));
        verify(taskRepository, never()).findByUser(any(), any());
    }

    @Test
    void shouldUseSpecificationWhenBothStatusAndPriorityProvided() {
        User user = User.builder().id(1L).build();
        Page<Task> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(taskRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        taskService.getTasksPaginated(user, 0, 20, Task.Status.TODO, Task.Priority.LOW, null);

        verify(taskRepository).findAll(any(Specification.class), any(Pageable.class));
        verify(taskRepository, never()).findByUser(any(), any());
    }

    @Test
    void shouldMarkPastDueTodoTaskAsOverdue() {
        User user = User.builder().id(1L).build();
        var request = com.example.todolist.dto.task.TaskCreateRequest.builder()
                .title("Past due")
                .dueDate(LocalDate.now().minusDays(1))
                .build();
        when(taskRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = taskService.createTask(request, user);

        assertTrue(response.isOverdue());
    }

    @Test
    void shouldNotMarkFutureDatedTaskAsOverdue() {
        User user = User.builder().id(1L).build();
        var request = com.example.todolist.dto.task.TaskCreateRequest.builder()
                .title("Future")
                .dueDate(LocalDate.now().plusDays(1))
                .build();
        when(taskRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = taskService.createTask(request, user);

        assertFalse(response.isOverdue());
    }

    @Test
    void shouldNotMarkPastDueDoneTaskAsOverdue() {
        User user = User.builder().id(1L).build();
        var request = com.example.todolist.dto.task.TaskCreateRequest.builder()
                .title("Done past due")
                .status(Task.Status.DONE)
                .dueDate(LocalDate.now().minusDays(1))
                .build();
        when(taskRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = taskService.createTask(request, user);

        assertFalse(response.isOverdue());
    }

    @Test
    void shouldNotMarkTaskWithoutDueDateAsOverdue() {
        User user = User.builder().id(1L).build();
        var request = com.example.todolist.dto.task.TaskCreateRequest.builder()
                .title("No due date")
                .build();
        when(taskRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = taskService.createTask(request, user);

        assertFalse(response.isOverdue());
    }

    @Test
    void shouldNotMarkTaskDueTodayAsOverdue() {
        User user = User.builder().id(1L).build();
        var request = com.example.todolist.dto.task.TaskCreateRequest.builder()
                .title("Due today")
                .dueDate(LocalDate.now())
                .build();
        when(taskRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = taskService.createTask(request, user);

        assertFalse(response.isOverdue());
    }
}
