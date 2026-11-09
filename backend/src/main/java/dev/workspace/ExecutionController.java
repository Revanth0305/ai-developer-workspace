package dev.workspace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ExecutionController {
    private final ExecutionService executions;
    private final ExecutionStore store;
    private final ExecutionAttemptRepository attempts;
    private final DockerSandbox sandbox;
    public ExecutionController(ExecutionService executions,ExecutionStore store,ExecutionAttemptRepository attempts,DockerSandbox sandbox) {
        this.executions=executions;this.store=store;this.attempts=attempts;this.sandbox=sandbox;
    }
    public record ContextInput(@Size(max=8) List<@NotBlank @Size(max=2048) String> files,
        @Size(max=8) List<@NotBlank @Size(max=2048) String> newFiles,UUID repairOf) {}
    public record ProposalInput(@Size(max=8) List<@NotBlank @Size(max=2048) String> files,
        @Size(max=8) List<@NotBlank @Size(max=2048) String> newFiles,UUID repairOf,@NotBlank @Size(min=64,max=64) String contextHash) {}
    public record CheckInput(@NotBlank @Size(min=64,max=64) String reviewHash,@NotBlank @Pattern(regexp="sha256:[a-f0-9]{64}") String imageId) {}
    @GetMapping("/execution/status") public DockerSandbox.Status status() {return sandbox.status(false);}
    @PostMapping("/execution/status/refresh") public DockerSandbox.Status refresh() {return sandbox.status(true);}
    @PostMapping("/tasks/{id}/execution-context") public ExecutionService.Preview context(@PathVariable UUID id,@Valid @RequestBody ContextInput input) throws Exception {
        return executions.preview(id,input.files(),input.newFiles(),input.repairOf());
    }
    @PostMapping("/tasks/{id}/executions") @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionAttempt propose(@PathVariable UUID id,@Valid @RequestBody ProposalInput input) throws Exception {
        return executions.propose(id,input.files(),input.newFiles(),input.repairOf(),input.contextHash());
    }
    @GetMapping("/tasks/{id}/executions") public List<ExecutionAttempt> list(@PathVariable UUID id) {return executions.list(id);}
    @GetMapping("/executions/{id}") public ExecutionAttempt get(@PathVariable UUID id) {return store.get(id);}
    @GetMapping("/executions/{id}/logs") public Map<String,String> logs(@PathVariable UUID id) {return Map.of("logs",executions.logs(id));}
    @PostMapping("/executions/{id}/checks") @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionAttempt check(@PathVariable UUID id,@Valid @RequestBody CheckInput input) throws Exception {return executions.check(id,input.reviewHash(),input.imageId());}
    @PostMapping("/executions/{id}/cancel") public ExecutionAttempt cancel(@PathVariable UUID id) {return executions.cancel(id);}
    @PostMapping("/executions/{id}/cleanup") public ExecutionAttempt cleanup(@PathVariable UUID id) {return executions.cleanup(id);}
    @GetMapping("/executions/{id}/patch") public ResponseEntity<String> patch(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"candidate-"+id+".patch\"").body(executions.patch(id));
    }
}
