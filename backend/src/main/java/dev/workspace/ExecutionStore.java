package dev.workspace;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

@Service
public class ExecutionStore {
    private final ExecutionAttemptRepository attempts;
    private final TaskRepository tasks;
    private final ActivityRepository activity;
    public ExecutionStore(ExecutionAttemptRepository attempts,TaskRepository tasks,ActivityRepository activity) {
        this.attempts=attempts;this.tasks=tasks;this.activity=activity;
    }
    @Transactional public ExecutionAttempt create(ExecutionAttempt attempt) {
        ExecutionAttempt saved=attempts.save(attempt);log(saved,"Isolated change proposal requested");return saved;
    }
    @Transactional public ExecutionAttempt update(UUID id,Consumer<ExecutionAttempt> change,String message) {
        ExecutionAttempt attempt=get(id);change.accept(attempt);attempt.updatedAt=Instant.now();
        if(message!=null) log(attempt,message);return attempts.save(attempt);
    }
    public ExecutionAttempt get(UUID id) { return attempts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Execution attempt not found.")); }
    private void log(ExecutionAttempt attempt,String message) {
        Task task=tasks.findById(attempt.taskId).orElseThrow();activity.save(new Activity(task.projectId,task.id,message));
    }
}
