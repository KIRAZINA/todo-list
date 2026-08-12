package com.example.todolist.repository;

import com.example.todolist.entity.Task;
import com.example.todolist.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    @Override
    @EntityGraph(attributePaths = "user")
    Optional<Task> findById(Long id);

    @EntityGraph(attributePaths = "user")
    Page<Task> findByUser(User user, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<Task> findAll(Specification<Task> spec, Pageable pageable);
}
