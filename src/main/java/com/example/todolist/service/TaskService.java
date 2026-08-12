package com.example.todolist.service;

import com.example.todolist.dto.task.PaginatedTaskResponse;
import com.example.todolist.dto.task.TaskCreateRequest;
import com.example.todolist.dto.task.TaskResponse;
import com.example.todolist.dto.task.TaskUpdateRequest;
import com.example.todolist.entity.Task;
import com.example.todolist.entity.User;
import com.example.todolist.exception.ResourceNotFoundException;
import com.example.todolist.repository.TaskRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    @Transactional
    public TaskResponse createTask(TaskCreateRequest request, User user) {
        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(request.getPriority() != null ? request.getPriority() : Task.Priority.MEDIUM)
                .status(request.getStatus() != null ? request.getStatus() : Task.Status.TODO)
                .dueDate(request.getDueDate())
                .user(user)
                .build();

        task = taskRepository.save(task);
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long id, User currentUser) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        if (!task.getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Task not found");
        }
        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public PaginatedTaskResponse getTasksPaginated(User currentUser, int page, int size,
                                                   Task.Status status, Task.Priority priority, Boolean overdue) {
        boolean overdueActive = Boolean.TRUE.equals(overdue);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Task> taskPage;
        if (status == null && priority == null && !overdueActive) {
            taskPage = taskRepository.findByUser(currentUser, pageable);
        } else {
            taskPage = taskRepository.findAll(taskFilter(currentUser, status, priority, overdueActive), pageable);
        }

        List<TaskResponse> content = taskPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PaginatedTaskResponse.builder()
                .content(content)
                .number(taskPage.getNumber())
                .size(taskPage.getSize())
                .totalElements(taskPage.getTotalElements())
                .totalPages(taskPage.getTotalPages())
                .first(taskPage.isFirst())
                .last(taskPage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public PaginatedTaskResponse getAllTasksPaginated(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Task> taskPage = taskRepository.findAll(pageable);

        List<TaskResponse> content = taskPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PaginatedTaskResponse.builder()
                .content(content)
                .number(taskPage.getNumber())
                .size(taskPage.getSize())
                .totalElements(taskPage.getTotalElements())
                .totalPages(taskPage.getTotalPages())
                .first(taskPage.isFirst())
                .last(taskPage.isLast())
                .build();
    }

    @Transactional
    public TaskResponse updateTask(Long id, TaskUpdateRequest request, User currentUser) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        if (!task.getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Task not found");
        }

        if (request.isTitleProvided() || request.getTitle() != null) {
            task.setTitle(request.getTitle());
        }
        if (request.isDescriptionProvided() || request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.isPriorityProvided() || request.getPriority() != null) {
            task.setPriority(request.getPriority());
        }
        if (request.isStatusProvided() || request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }
        if (request.isDueDateProvided() || request.getDueDate() != null) {
            task.setDueDate(request.getDueDate());
        }

        task = taskRepository.save(task);
        return toResponse(task);
    }

    @Transactional
    public void deleteTask(Long id, User currentUser) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        if (!task.getUser().getId().equals(currentUser.getId())) {
            throw new ResourceNotFoundException("Task not found");
        }
        taskRepository.delete(task);
    }

    private Specification<Task> taskFilter(User currentUser, Task.Status status, Task.Priority priority,
                                           boolean overdueActive) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), currentUser.getId()));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (overdueActive) {
                predicates.add(cb.lessThan(root.get("dueDate"), LocalDate.now()));
                predicates.add(cb.notEqual(root.get("status"), Task.Status.DONE));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private TaskResponse toResponse(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .priority(task.getPriority())
                .status(task.getStatus())
                .dueDate(task.getDueDate())
                .overdue(isOverdue(task))
                .userId(task.getUser().getId())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private boolean isOverdue(Task task) {
        return task.getDueDate() != null
                && task.getStatus() != Task.Status.DONE
                && task.getDueDate().isBefore(LocalDate.now());
    }
}
