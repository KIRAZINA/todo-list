package com.example.todolist.service;

import com.example.todolist.entity.Task;
import com.example.todolist.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDueDateScheduler {

    private final TaskRepository taskRepository;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional(readOnly = true)
    public void checkUpcomingDueDates() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        List<Task> tasks = taskRepository.findAll();
        for (Task task : tasks) {
            if (task.getDueDate() == null || task.getStatus() == Task.Status.DONE) {
                continue;
            }
            if (task.getDueDate().equals(today) || task.getDueDate().equals(tomorrow)) {
                log.info("Reminder: task '{}' (id={}) is due on {} for user '{}'",
                        task.getTitle(), task.getId(), task.getDueDate(),
                        task.getUser() != null ? task.getUser().getUsername() : "unknown");
            }
        }
    }
}
