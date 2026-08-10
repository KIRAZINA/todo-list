package com.example.todo.repository;

import com.example.todo.entity.Task;
import com.example.todo.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    @Override
    @EntityGraph(attributePaths = "user")
    Optional<Task> findById(Long id);

    @EntityGraph(attributePaths = "user")
    Page<Task> findByUser(User user, Pageable pageable);
}
