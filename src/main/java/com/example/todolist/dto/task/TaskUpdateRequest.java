package com.example.todolist.dto.task;

import com.example.todolist.dto.task.SubtaskDto;
import com.example.todolist.entity.Task;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

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
    private Set<String> tags;
    private List<SubtaskDto> subtasks;
    @JsonIgnore private boolean titleProvided;
    @JsonIgnore private boolean descriptionProvided;
    @JsonIgnore private boolean priorityProvided;
    @JsonIgnore private boolean statusProvided;
    @JsonIgnore private boolean dueDateProvided;
    @JsonIgnore private boolean tagsProvided;
    @JsonIgnore private boolean subtasksProvided;
    public void setTitle(String value) { titleProvided = true; title = value; }
    public void setDescription(String value) { descriptionProvided = true; description = value; }
    public void setPriority(Task.Priority value) { priorityProvided = true; priority = value; }
    public void setStatus(Task.Status value) { statusProvided = true; status = value; }
    public void setDueDate(LocalDate value) { dueDateProvided = true; dueDate = value; }
    public void setTags(Set<String> value) { tagsProvided = true; tags = value; }
    public void setSubtasks(List<SubtaskDto> value) { subtasksProvided = true; subtasks = value; }
    public boolean isTitleProvided() { return titleProvided; }
    public boolean isDescriptionProvided() { return descriptionProvided; }
    public boolean isPriorityProvided() { return priorityProvided; }
    public boolean isStatusProvided() { return statusProvided; }
    public boolean isDueDateProvided() { return dueDateProvided; }
    public boolean isTagsProvided() { return tagsProvided; }
    public boolean isSubtasksProvided() { return subtasksProvided; }
}