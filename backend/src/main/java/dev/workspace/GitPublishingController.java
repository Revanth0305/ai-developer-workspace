package dev.workspace;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/executions/{id}/git")
public class GitPublishingController {
    private final GitPublishing git;
    public GitPublishingController(GitPublishing git) {this.git=git;}
    public record Review(String hash) {}
    @GetMapping public Map<String,Object> get(@PathVariable UUID id) {return Map.of("enabled",git.enabled(),"publication",git.get(id).map(p->(Object)p).orElse(Map.of()));}
    @PostMapping("/preview") public GitPublication preview(@PathVariable UUID id,@RequestBody GitPublishing.Input input) throws Exception {return git.prepare(id,input);}
    @PostMapping("/commit") public GitPublication commit(@PathVariable UUID id,@RequestBody Review review) throws Exception {return git.commit(id,review.hash());}
    @PostMapping("/push") public GitPublication push(@PathVariable UUID id,@RequestBody Review review) throws Exception {return git.push(id,review.hash());}
}
