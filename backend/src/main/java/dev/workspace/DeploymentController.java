package dev.workspace;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api")
public class DeploymentController {
    private final DeploymentService service;
    private final DeploymentStore store;
    public DeploymentController(DeploymentService service,DeploymentStore store){this.service=service;this.store=store;}
    public record Review(String hash){}
    @GetMapping("/deployment/status") public Map<String,Object> status(){return Map.of("enabled",service.enabled(),"profile","LOCAL_STATIC_NODE24");}
    @GetMapping("/projects/{id}/deployments") public Map<String,Object> project(@PathVariable UUID id){return Map.of("enabled",service.enabled(),"attempts",service.listProject(id));}
    @GetMapping("/executions/{id}/deployments") public Map<String,Object> list(@PathVariable UUID id){return Map.of("enabled",service.enabled(),"attempts",service.list(id));}
    @PostMapping("/executions/{id}/deployments/preview") public Deployment preview(@PathVariable UUID id)throws Exception{return service.preview(id);}
    @GetMapping("/deployments/{id}") public Deployment get(@PathVariable UUID id){return store.get(id);}
    @PostMapping("/deployments/{id}/start") public Deployment start(@PathVariable UUID id,@RequestBody Review review)throws Exception{return service.start(id,review.hash());}
    @PostMapping("/deployments/{id}/refresh") public Deployment refresh(@PathVariable UUID id){return service.refresh(id);}
    @PostMapping("/deployments/{id}/stop") public Deployment stop(@PathVariable UUID id){return service.stop(id);}
    @PostMapping("/deployments/{id}/cleanup") public Deployment cleanup(@PathVariable UUID id){return service.cleanup(id);}
}
