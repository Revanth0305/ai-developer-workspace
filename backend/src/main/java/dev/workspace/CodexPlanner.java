package dev.workspace;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@Service
public class CodexPlanner {
    public record ConnectionStatus(boolean enabled, boolean authenticated, String authMode, String message) {}
    private final String executable;
    private final boolean enabled;
    private final Path workingDirectory;
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final PlanAttemptRepository attempts;
    private final ActivityRepository activity;
    private final PlanContext context;
    private volatile ConnectionStatus cached;
    private Instant checkedAt = Instant.EPOCH;
    private UUID activeTask;
    private UUID activeAttempt;
    private volatile CodexConnection activeConnection;
    private final int timeoutSeconds;

    public CodexPlanner(@Value("${workspace.codex.executable:codex}") String executable,
                        @Value("${workspace.codex.enabled:true}") boolean enabled,
                        @Value("${workspace.codex.timeout-seconds:240}") int timeoutSeconds,
                        @Value("${workspace.platform-root}") String platformRoot,
                        TaskRepository tasks, ProjectRepository projects, PlanAttemptRepository attempts,
                        ActivityRepository activity, PlanContext context) throws Exception {
        this.executable = executable; this.enabled = enabled; this.timeoutSeconds = Math.max(30, Math.min(600, timeoutSeconds));
        this.tasks = tasks; this.projects = projects; this.attempts = attempts; this.activity = activity; this.context = context;
        workingDirectory = Path.of(platformRoot).toRealPath().resolve(".runtime/codex-planning");
        Files.createDirectories(workingDirectory);
        cached = new ConnectionStatus(enabled, false, "", enabled ? "Codex connection has not been checked." : "Codex is disabled for this session.");
    }

    public ConnectionStatus snapshot() { return cached; }
    public synchronized ConnectionStatus status() {
        if (!enabled || checkedAt.plusSeconds(60).isAfter(Instant.now())) return cached;
        checkedAt = Instant.now();
        try (var client = new CodexConnection(executable, workingDirectory)) {
            client.initialize();
            var account = client.request("account/read", Map.of("refreshToken", false)).path("account");
            boolean authenticated = !account.isMissingNode() && !account.isNull();
            cached = new ConnectionStatus(true, authenticated, account.path("type").asText(), authenticated ? "Codex is signed in locally. Planning is available." : "Sign in with Codex on this computer, then refresh the connection.");
        } catch (Exception e) {
            cached = new ConnectionStatus(true, false, "", "Codex could not be reached. Check the executable path and run codex login status in a terminal.");
        }
        return cached;
    }

    public synchronized PlanAttempt start(UUID taskId, List<String> selectedFiles, String expectedHash) {
        if (activeTask != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Another planning task is running. Wait or cancel it first.");
        if (!status().authenticated()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, cached.message());
        Task task = task(taskId);
        if (!Set.of("DRAFT", "FAILED", "PLANNED").contains(task.status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "This task cannot start planning in its current state.");
        Project project = projects.findById(task.projectId).orElseThrow();
        PlanContext.Preview preview = context.preview(project, task, selectedFiles);
        if (!preview.hash().equals(expectedHash)) throw new ResponseStatusException(HttpStatus.CONFLICT, "The context changed. Review the updated request before sending it.");
        PlanAttempt attempt = attempts.save(new PlanAttempt(task.id, preview.prompt()));
        task.status = "PLANNING"; task.updatedAt = Instant.now(); tasks.save(task);
        activity.save(new Activity(task.projectId, task.id, "Read-only Codex planning started"));
        activeTask = task.id; activeAttempt = attempt.id;
        Thread.ofVirtual().name("workspace-codex-plan").start(() -> execute(attempt.id, preview.prompt()));
        return attempt;
    }

    private void execute(UUID attemptId, String prompt) {
        try (var client = new CodexConnection(executable, workingDirectory)) {
            synchronized (this) {
                if (!attemptId.equals(activeAttempt)) return;
                activeConnection = client;
            }
            client.initialize();
            String result = client.plan(prompt, workingDirectory, Duration.ofSeconds(timeoutSeconds));
            finish(attemptId, "SUCCEEDED", result, "", client.threadId());
        } catch (Exception e) {
            String error = e instanceof java.util.concurrent.TimeoutException ? "Codex planning exceeded its time limit. You can retry the task." : "Codex could not complete this plan. Check Codex sign-in, account limits and connectivity, then retry.";
            finish(attemptId, "FAILED", "", error, null);
        }
    }

    private synchronized void finish(UUID attemptId, String status, String result, String error, String threadId) {
        PlanAttempt attempt = attempts.findById(attemptId).orElseThrow();
        if (!attempt.status.equals("RUNNING")) return;
        attempt.status = status; attempt.result = result; attempt.error = error; attempt.threadId = threadId; attempt.finishedAt = Instant.now(); attempts.save(attempt);
        Task task = task(attempt.taskId); task.status = status.equals("SUCCEEDED") ? "PLANNED" : "FAILED"; task.updatedAt = Instant.now(); tasks.save(task);
        activity.save(new Activity(task.projectId, task.id, status.equals("SUCCEEDED") ? "Codex plan is ready for review; no project files were changed" : "Codex planning failed; no project files were changed"));
        if (attemptId.equals(activeAttempt)) { activeAttempt = null; activeTask = null; activeConnection = null; }
    }

    public synchronized Task cancel(UUID taskId) {
        Task task = task(taskId);
        if (!task.status.equals("DRAFT") && !task.status.equals("PLANNING")) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft or planning tasks can be cancelled.");
        if (task.status.equals("PLANNING")) {
            PlanAttempt attempt = attempts.findById(activeAttempt).orElseThrow();
            attempt.status = "CANCELLED"; attempt.finishedAt = Instant.now(); attempts.save(attempt);
            CodexConnection connection = activeConnection;
            activeTask = null; activeAttempt = null; activeConnection = null;
            if (connection != null) Thread.ofVirtual().start(connection::interrupt);
        }
        task.status = "CANCELLED"; task.updatedAt = Instant.now(); tasks.save(task);
        activity.save(new Activity(task.projectId, task.id, "Task cancelled"));
        return task;
    }

    @EventListener(ApplicationReadyEvent.class) public synchronized void recover() {
        for (PlanAttempt attempt : attempts.findByStatus("RUNNING")) {
            attempt.status = "INTERRUPTED"; attempt.error = "The backend stopped during planning. Review the context and retry."; attempt.finishedAt = Instant.now(); attempts.save(attempt);
            Task task = task(attempt.taskId); task.status = "FAILED"; task.updatedAt = Instant.now(); tasks.save(task);
            activity.save(new Activity(task.projectId, task.id, "Interrupted Codex plan recovered after backend restart"));
        }
    }
    @PreDestroy public void shutdown() { CodexConnection client = activeConnection; if (client != null) client.close(); }
    private Task task(UUID id) { return tasks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found.")); }
}
