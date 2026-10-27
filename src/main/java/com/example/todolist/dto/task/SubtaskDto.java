package com.example.todolist.dto.task;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubtaskDto {
    private Long id;
    private String title;
    private boolean completed;
}
