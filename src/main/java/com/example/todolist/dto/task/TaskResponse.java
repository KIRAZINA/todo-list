package com.example.todolist.dto.task;

import com.example.todolist.dto.task.SubtaskDto;
import com.example.todolist.entity.Task;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskResponse {

    private Long id;
    private String title;
    private String description;
    private Task.Priority priority;
    private Task.Status status;
    private LocalDate dueDate;
    private boolean overdue;
    private boolean nearDueDate;
    private Long userId;
    private String ownerUsername;
    private Set<String> tags;
    private List<SubtaskDto> subtasks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
