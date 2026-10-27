package com.example.todolist.dto.task;

import com.example.todolist.dto.task.SubtaskDto;
import com.example.todolist.entity.Task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskCreateRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 100)
    private String title;

    @Size(max = 1000)
    private String description;

    private Task.Priority priority;

    private Task.Status status;

    private LocalDate dueDate;

    private Set<String> tags;

    private List<SubtaskDto> subtasks;
}
