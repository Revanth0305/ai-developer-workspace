package dev.workspace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api")
public class WorkspaceController {
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final ActivityRepository activity;
    private final WorkspaceFiles files;
    private final GitInspector git;
    private final CodexPlanner codex;
    private final PlanContext context;
    private final PlanAttemptRepository attempts;
    private final DockerSandbox sandbox;
    public WorkspaceController(ProjectRepository projects, TaskRepository tasks, ActivityRepository activity, WorkspaceFiles files, GitInspector git, CodexPlanner codex, PlanContext context, PlanAttemptRepository attempts, DockerSandbox sandbox) {
        this.projects = projects; this.tasks = tasks; this.activity = activity; this.files = files; this.git = git;
        this.codex = codex; this.context = context; this.attempts = attempts;
        this.sandbox = sandbox;
    }
    public record ProjectInput(@NotBlank @Size(max=100) String name, @NotBlank @Size(max=2048) String directory, @Size(max=8000) String instructions) {}
    public record SettingsInput(@NotBlank @Size(max=100) String name, @Size(max=8000) String instructions) {}
    public record TaskInput(@NotNull UUID projectId, @NotBlank @Size(max=12000) String instruction) {}
    public record ContextInput(@Size(max=8) List<@NotBlank @Size(max=2048) String> files) {}
    public record PlanInput(@Size(max=8) List<@NotBlank @Size(max=2048) String> files, @NotBlank @Size(max=64) String contextHash) {}

    @GetMapping("/health") public Map<String, Object> health() {
        return Map.of("status", "UP", "application", "AI Developer Workspace", "time", Instant.now(), "aiProvider", "codex", "aiConfigured", codex.snapshot().authenticated(), "executionEnabled", sandbox.enabled());
    }
    @GetMapping("/ai/status") public CodexPlanner.ConnectionStatus aiStatus() { return codex.status(); }
    @PostMapping("/ai/status/refresh") public CodexPlanner.ConnectionStatus refreshAiStatus() { return codex.refreshStatus(); }
    @GetMapping("/projects") public List<Project> projects() { return projects.findAllByOrderByCreatedAtDesc(); }
    @GetMapping("/projects/{id}") public Project project(@PathVariable UUID id) {
        return projects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    }
    @PostMapping("/projects") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Project create(@Valid @RequestBody ProjectInput input) {
        Path directory = files.registerDirectory(input.directory());
        for (Project existing : projects.findAll()) {
            Path other = Path.of(existing.directory);
            if (directory.startsWith(other) || other.startsWith(directory)) throw WorkspaceFiles.bad("That folder overlaps an already registered project.");
        }
        Project saved = projects.save(new Project(input.name().strip(), directory.toString(), clean(input.instructions())));
        activity.save(new Activity(saved.id, null, "Project registered: " + saved.name));
        return saved;
    }
    @PutMapping("/projects/{id}") @Transactional
    public Project settings(@PathVariable UUID id, @Valid @RequestBody SettingsInput input) {
        Project project = project(id);
        project.name = input.name().strip(); project.instructions = clean(input.instructions()); project.updatedAt = Instant.now();
        activity.save(new Activity(id, null, "Project settings updated"));
        return projects.save(project);
    }
    @GetMapping("/projects/{id}/git") public GitInspector.Status git(@PathVariable UUID id) {
        return git.inspect(files.registerDirectory(project(id).directory));
    }
    @GetMapping("/projects/{id}/files") public List<String> files(@PathVariable UUID id) { return files.list(project(id).directory); }
    @GetMapping("/projects/{id}/file") public Map<String, String> file(@PathVariable UUID id, @RequestParam String path) { return files.read(project(id).directory, path); }
    @GetMapping("/tasks") public List<Task> tasks(@RequestParam(required=false) UUID projectId) {
        if (projectId != null) { project(projectId); return tasks.findByProjectIdOrderByCreatedAtDesc(projectId); }
        return tasks.findAllByOrderByCreatedAtDesc();
    }
    @PostMapping("/tasks") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Task createTask(@Valid @RequestBody TaskInput input) {
        project(input.projectId());
        Task task = tasks.save(new Task(input.projectId(), input.instruction().strip()));
        activity.save(new Activity(task.projectId, task.id, "Task saved as draft"));
        return task;
    }
    @PostMapping("/tasks/{id}/cancel") public Task cancel(@PathVariable UUID id) { return codex.cancel(id); }
    @GetMapping("/tasks/{id}") public Task task(@PathVariable UUID id) {
        return tasks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found."));
    }
    @PostMapping("/tasks/{id}/context") public PlanContext.Preview context(@PathVariable UUID id, @Valid @RequestBody ContextInput input) {
        Task task = task(id); return context.preview(project(task.projectId), task, input.files());
    }
    @PostMapping("/tasks/{id}/plan") @ResponseStatus(HttpStatus.ACCEPTED)
    public PlanAttempt plan(@PathVariable UUID id, @Valid @RequestBody PlanInput input) { return codex.start(id, input.files(), input.contextHash()); }
    @GetMapping("/tasks/{id}/attempts") public List<PlanAttempt> attempts(@PathVariable UUID id) {
        task(id); return attempts.findByTaskIdOrderByStartedAtDesc(id);
    }
    @GetMapping("/activity") public List<Activity> activity(@RequestParam(required=false) UUID projectId) {
        if (projectId != null) { project(projectId); return activity.findTop50ByProjectIdOrderByCreatedAtDesc(projectId); }
        return activity.findTop50ByOrderByCreatedAtDesc();
    }
    private String clean(String value) { return value == null ? "" : value.strip(); }
}
