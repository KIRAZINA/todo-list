package com.example.todolist.service;

import com.example.todolist.dto.task.PaginatedTaskResponse;
import com.example.todolist.dto.task.TaskCreateRequest;
import com.example.todolist.dto.task.TaskResponse;
import com.example.todolist.dto.task.TaskUpdateRequest;
import com.example.todolist.dto.task.SubtaskDto;
import com.example.todolist.entity.Subtask;
import com.example.todolist.entity.Tag;
import com.example.todolist.entity.Task;
import com.example.todolist.entity.User;
import com.example.todolist.repository.TagRepository;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TagRepository tagRepository;

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

        if (request.getTags() != null) {
            for (String name : request.getTags()) {
                Tag tag = tagRepository.findByName(name).orElseGet(() -> tagRepository.save(Tag.builder().name(name).build()));
                task.getTags().add(tag);
            }
        }
        if (request.getSubtasks() != null) {
            for (SubtaskDto dto : request.getSubtasks()) {
                Subtask st = Subtask.builder().task(task).title(dto.getTitle()).completed(dto.isCompleted()).build();
                task.getSubtasks().add(st);
            }
        }

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
                                                   Task.Status status, Task.Priority priority, Boolean overdue,
                                                   LocalDate dueBefore, LocalDate dueAfter,
                                                   TaskSortField sortField, String direction) {
        boolean overdueActive = Boolean.TRUE.equals(overdue);
        TaskSortField effectiveSortField = sortField != null ? sortField : TaskSortField.createdAt;
        String effectiveDirection = direction != null ? direction : "desc";
        Sort.Direction sortDirection = Sort.Direction.fromString(effectiveDirection);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, effectiveSortField.getEntityProperty()));
        Page<Task> taskPage;
        if (status == null && priority == null && !overdueActive && dueBefore == null && dueAfter == null) {
            taskPage = taskRepository.findByUser(currentUser, pageable);
        } else {
            taskPage = taskRepository.findAll(
                    buildTaskSpec(currentUser, status, priority, overdueActive, dueBefore, dueAfter), pageable);
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
    public PaginatedTaskResponse getAllTasksPaginated(int page, int size,
                                                      Task.Status status, Task.Priority priority, Boolean overdue,
                                                      LocalDate dueBefore, LocalDate dueAfter,
                                                      TaskSortField sortField, String direction) {
        boolean overdueActive = Boolean.TRUE.equals(overdue);
        TaskSortField effectiveSortField = sortField != null ? sortField : TaskSortField.createdAt;
        String effectiveDirection = direction != null ? direction : "desc";
        Sort.Direction sortDirection = Sort.Direction.fromString(effectiveDirection);
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, effectiveSortField.getEntityProperty()));
        // Owner is null: the spec omits the ownership predicate, so every
        // user's tasks are matched. The repository's @EntityGraph keeps the
        // owner eager-loaded for ownerUsername mapping.
        Page<Task> taskPage = taskRepository.findAll(
                buildTaskSpec(null, status, priority, overdueActive, dueBefore, dueAfter), pageable);

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

        if (request.isTagsProvided()) {
            task.getTags().clear();
            if (request.getTags() != null) {
                for (String name : request.getTags()) {
                    Tag tag = tagRepository.findByName(name).orElseGet(() -> tagRepository.save(Tag.builder().name(name).build()));
                    task.getTags().add(tag);
                }
            }
        }
        if (request.isSubtasksProvided()) {
            task.getSubtasks().clear();
            if (request.getSubtasks() != null) {
                for (SubtaskDto dto : request.getSubtasks()) {
                    Subtask st = Subtask.builder().task(task).title(dto.getTitle()).completed(dto.isCompleted()).build();
                    if (dto.getId() != null) st.setId(dto.getId());
                    task.getSubtasks().add(st);
                }
            }
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

    private Specification<Task> buildTaskSpec(User owner, Task.Status status, Task.Priority priority,
                                              boolean overdueActive, LocalDate dueBefore, LocalDate dueAfter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // A non-null owner restricts the result to that owner's tasks;
            // a null owner (admin cross-user listing) omits the predicate,
            // matching every user's tasks.
            if (owner != null) {
                predicates.add(cb.equal(root.get("user").get("id"), owner.getId()));
            }
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
            if (dueBefore != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), dueBefore));
            }
            if (dueAfter != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), dueAfter));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private TaskResponse toResponse(Task task) {
        List<SubtaskDto> subtaskDtos = task.getSubtasks().stream()
                .map(s -> SubtaskDto.builder().id(s.getId()).title(s.getTitle()).completed(s.isCompleted()).build())
                .collect(Collectors.toList());
        Set<String> tagNames = task.getTags().stream().map(Tag::getName).collect(Collectors.toSet());

        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .priority(task.getPriority())
                .status(task.getStatus())
                .dueDate(task.getDueDate())
                .overdue(isOverdue(task))
                .nearDueDate(isNearDueDate(task))
                .userId(task.getUser().getId())
                .ownerUsername(task.getUser().getUsername())
                .tags(tagNames)
                .subtasks(subtaskDtos)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private boolean isNearDueDate(Task task) {
        return task.getDueDate() != null
                && task.getStatus() != Task.Status.DONE
                && !task.getDueDate().isAfter(LocalDate.now().plusDays(1));
    }

    private boolean isOverdue(Task task) {
        return task.getDueDate() != null
                && task.getStatus() != Task.Status.DONE
                && task.getDueDate().isBefore(LocalDate.now());
    }
}
