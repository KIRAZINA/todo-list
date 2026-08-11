package com.example.todo.dto.user;

import lombok.*;

import java.util.List;

/**
 * Paginated response for admin user list queries.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaginatedUserResponse {
    private List<UserResponse> content;
    private int number;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
}
