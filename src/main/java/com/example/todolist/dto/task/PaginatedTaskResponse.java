package com.example.todolist.dto.task;

import lombok.*;

import java.util.List;

/**
 * Paginated response for task list queries.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaginatedTaskResponse {
    private List<TaskResponse> content;
    private int number;        // current page number (0-indexed)
    private int size;          // page size
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
}
