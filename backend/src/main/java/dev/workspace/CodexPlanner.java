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
    private final PlanContext context;
    private final PlanLifecycle lifecycle;
    private final PlanningClientFactory clients;
    private final PlanDocument documents;
    private final Object statusLock = new Object();
    private volatile ConnectionStatus cached;
    private Instant checkedAt = Instant.EPOCH;
    private Run active;
    private boolean stopping;
    private final int timeoutSeconds;

    private static final class Run {
        final UUID taskId;
        final UUID attemptId;
        boolean cancelled;
        PlanningClient client;
        Run(UUID taskId, UUID attemptId) { this.taskId = taskId; this.attemptId = attemptId; }
    }

    public CodexPlanner(@Value("${workspace.codex.executable:codex}") String executable,
                        @Value("${workspace.codex.enabled:false}") boolean enabled,
                        @Value("${workspace.codex.timeout-seconds:240}") int timeoutSeconds,
                        @Value("${workspace.platform-root}") String platformRoot,
                        TaskRepository tasks, ProjectRepository projects, PlanContext context,
                        PlanLifecycle lifecycle, PlanningClientFactory clients, PlanDocument documents) throws Exception {
        this.executable = executable; this.enabled = enabled; this.timeoutSeconds = Math.max(30, Math.min(600, timeoutSeconds));
        this.tasks = tasks; this.projects = projects; this.context = context;
        this.lifecycle = lifecycle; this.clients = clients; this.documents = documents;
        workingDirectory = Path.of(platformRoot).toRealPath().resolve(".runtime/codex-planning");
        Files.createDirectories(workingDirectory);
        cached = new ConnectionStatus(enabled, false, "", enabled ? "Check the Codex connection to enable planning." : "Codex is disabled. Start the backend with WORKSPACE_CODEX_ENABLED=true to enable read-only planning.");
    }

    public ConnectionStatus snapshot() { return cached; }
    public ConnectionStatus refreshStatus() {
        synchronized (statusLock) { checkedAt = Instant.EPOCH; return status(); }
    }
    public ConnectionStatus status() {
        synchronized (statusLock) {
            if (!enabled || checkedAt.plusSeconds(60).isAfter(Instant.now())) return cached;
            try (var client = clients.create(executable, workingDirectory)) {
                client.initialize();
                var account = client.request("account/read", Map.of("refreshToken", false)).path("account");
                boolean authenticated = !account.isMissingNode() && !account.isNull();
                if (authenticated) client.prepare(workingDirectory);
                cached = new ConnectionStatus(true, authenticated, account.path("type").asText(), authenticated
                    ? "Codex is signed in. Read-only planning is available."
                    : "Sign in with Codex on this computer, then check the connection again.");
            } catch (Exception e) {
                cached = new ConnectionStatus(true, false, "", "Planning is unavailable. Check the Codex executable, local sign-in, and protocol compatibility.");
            }
            checkedAt = Instant.now();
            return cached;
        }
    }

    public PlanAttempt start(UUID taskId, List<String> selectedFiles, String expectedHash) {
        // Account/protocol checks never hold the lifecycle monitor and cannot block cancellation.
        if (!status().authenticated()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, cached.message());
        synchronized (this) {
            if (stopping) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The backend is shutting down.");
            if (active != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Another planning task is running or stopping. Wait or cancel it first.");
            Task task = task(taskId);
            Project project = projects.findById(task.projectId).orElseThrow();
            PlanContext.Preview preview = context.preview(project, task, selectedFiles);
            if (!preview.hash().equals(expectedHash)) throw new ResponseStatusException(HttpStatus.CONFLICT, "The context changed. Review the updated request before sending it.");
            PlanAttempt attempt = lifecycle.begin(task.id, preview.prompt());
            Run run = new Run(task.id, attempt.id);
            active = run;
            Thread.ofVirtual().name("workspace-codex-plan").start(() -> execute(run, project, preview.prompt()));
            return attempt;
        }
    }

    private void execute(Run run, Project project, String prompt) {
        try (var client = clients.create(executable, workingDirectory)) {
            synchronized (this) {
                if (run.cancelled || stopping) return;
                run.client = client;
            }
            client.initialize();
            String result = client.plan(prompt, workingDirectory, Duration.ofSeconds(timeoutSeconds));
            String markdown = documents.validateAndRender(project, result);
            synchronized (this) {
                if (!run.cancelled && !stopping) lifecycle.complete(run.attemptId, "SUCCEEDED", markdown, "", client.threadId());
            }
        } catch (Exception e) {
            synchronized (this) {
                if (!run.cancelled && !stopping) {
                    String error = e instanceof java.util.concurrent.TimeoutException
                        ? "Codex planning exceeded its time limit. Review the context and retry."
                        : "Codex could not return a valid read-only plan. Check sign-in, connectivity and account limits, then retry.";
                    lifecycle.complete(run.attemptId, "FAILED", "", error, null);
                }
            }
        } finally {
            synchronized (this) { if (active == run) active = null; }
        }
    }

    public Task cancel(UUID taskId) {
        PlanningClient client;
        Task cancelled;
        synchronized (this) {
            cancelled = lifecycle.cancel(taskId);
            client = active != null && active.taskId.equals(taskId) ? active.client : null;
            if (active != null && active.taskId.equals(taskId)) active.cancelled = true;
        }
        // Closing stops the owned child process; the slot stays occupied until its worker exits.
        if (client != null) client.interrupt();
        return cancelled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void recover() { lifecycle.recover(); }

    @PreDestroy
    public void shutdown() {
        PlanningClient client;
        synchronized (this) {
            stopping = true;
            if (active != null) active.cancelled = true;
            client = active == null ? null : active.client;
        }
        if (client != null) client.close();
    }

    private Task task(UUID id) {
        return tasks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found."));
    }
}
