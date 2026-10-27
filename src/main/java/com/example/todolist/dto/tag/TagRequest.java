package com.example.todolist.dto.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TagRequest {
    @NotBlank
    @Size(max = 50)
    private String name;

    @Size(max = 7)
    private String color;
}
