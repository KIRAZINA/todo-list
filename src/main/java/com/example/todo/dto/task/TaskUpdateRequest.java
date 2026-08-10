package com.example.todo.dto.task;

import com.example.todo.entity.Task;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskUpdateRequest {
    @Size(max = 100) private String title;
    @Size(max = 1000) private String description;
    private Task.Priority priority;
    private Task.Status status;
    private LocalDate dueDate;
    @JsonIgnore private boolean titleProvided;
    @JsonIgnore private boolean descriptionProvided;
    @JsonIgnore private boolean priorityProvided;
    @JsonIgnore private boolean statusProvided;
    @JsonIgnore private boolean dueDateProvided;
    public void setTitle(String value) { titleProvided = true; title = value; }
    public void setDescription(String value) { descriptionProvided = true; description = value; }
    public void setPriority(Task.Priority value) { priorityProvided = true; priority = value; }
    public void setStatus(Task.Status value) { statusProvided = true; status = value; }
    public void setDueDate(LocalDate value) { dueDateProvided = true; dueDate = value; }
    public boolean isTitleProvided() { return titleProvided; }
    public boolean isDescriptionProvided() { return descriptionProvided; }
    public boolean isPriorityProvided() { return priorityProvided; }
    public boolean isStatusProvided() { return statusProvided; }
    public boolean isDueDateProvided() { return dueDateProvided; }
}