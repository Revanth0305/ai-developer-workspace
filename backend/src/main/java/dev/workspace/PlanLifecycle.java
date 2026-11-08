package dev.workspace;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

/** Each transition commits before a worker is launched or its slot is released. */
@Service
public class PlanLifecycle {
    private final TaskRepository tasks;
    private final PlanAttemptRepository attempts;
    private final ActivityRepository activity;

    public PlanLifecycle(TaskRepository tasks, PlanAttemptRepository attempts, ActivityRepository activity) {
        this.tasks = tasks; this.attempts = attempts; this.activity = activity;
    }

    @Transactional
    public PlanAttempt begin(UUID taskId, String prompt) {
        Task task = task(taskId);
        if (!Set.of("DRAFT", "FAILED", "PLANNED").contains(task.status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This task cannot start planning in its current state.");
        PlanAttempt attempt = attempts.save(new PlanAttempt(taskId, prompt));
        task.status = "PLANNING"; task.updatedAt = Instant.now(); tasks.save(task);
        activity.save(new Activity(task.projectId, task.id, "Read-only Codex planning started"));
        return attempt;
    }

    @Transactional
    public void complete(UUID attemptId, String status, String result, String error, String threadId) {
        PlanAttempt attempt = attempts.findById(attemptId).orElseThrow();
        if (!attempt.status.equals("RUNNING")) return; // cancellation wins over a late result
        attempt.status = status; attempt.result = result; attempt.error = error;
        attempt.threadId = threadId; attempt.finishedAt = Instant.now(); attempts.save(attempt);
        Task task = task(attempt.taskId);
        task.status = status.equals("SUCCEEDED") ? "PLANNED" : "FAILED";
        task.updatedAt = Instant.now(); tasks.save(task);
        activity.save(new Activity(task.projectId, task.id, status.equals("SUCCEEDED")
            ? "Codex plan is ready for review; no project files were changed"
            : "Codex planning failed; no project files were changed"));
    }

    @Transactional
    public Task cancel(UUID taskId) {
        Task task = task(taskId);
        if (!Set.of("DRAFT", "PLANNING").contains(task.status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft or planning tasks can be cancelled.");
        for (PlanAttempt attempt : attempts.findByTaskIdOrderByStartedAtDesc(taskId)) {
            if (!attempt.status.equals("RUNNING")) continue;
            attempt.status = "CANCELLED"; attempt.finishedAt = Instant.now(); attempts.save(attempt);
        }
        task.status = "CANCELLED"; task.updatedAt = Instant.now(); tasks.save(task);
        activity.save(new Activity(task.projectId, task.id, "Task cancelled"));
        return task;
    }

    @Transactional
    public void recover() {
        for (PlanAttempt attempt : attempts.findByStatus("RUNNING")) {
            attempt.status = "INTERRUPTED";
            attempt.error = "The backend stopped during planning. Review the context and retry.";
            attempt.finishedAt = Instant.now(); attempts.save(attempt);
        }
        for (Task task : tasks.findAll()) {
            if (!task.status.equals("PLANNING")) continue;
            task.status = "FAILED"; task.updatedAt = Instant.now(); tasks.save(task);
            activity.save(new Activity(task.projectId, task.id, "Interrupted Codex plan recovered after backend restart"));
        }
    }

    private Task task(UUID id) {
        return tasks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found."));
    }
}
