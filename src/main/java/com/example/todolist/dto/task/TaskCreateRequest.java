package com.example.todolist.dto.task;

import com.example.todolist.entity.Task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

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
}
