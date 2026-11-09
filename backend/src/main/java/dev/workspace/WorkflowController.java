package dev.workspace;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api")
public class WorkflowController {
    private final WorkflowService service;
    public WorkflowController(WorkflowService service){this.service=service;}
    public record Start(WorkflowService.Input input,String hash){}
    @GetMapping("/workflow/status") public Map<String,Object> status(){return Map.of("enabled",service.enabled());}
    @GetMapping("/tasks/{id}/workflows") public List<WorkflowRun> list(@PathVariable UUID id){return service.list(id);}
    @PostMapping("/tasks/{id}/workflows/preview") public WorkflowService.Preview preview(@PathVariable UUID id,@RequestBody WorkflowService.Input input)throws Exception{return service.preview(id,input);}
    @PostMapping("/tasks/{id}/workflows") public WorkflowRun start(@PathVariable UUID id,@RequestBody Start start)throws Exception{return service.start(id,start.input(),start.hash());}
    @GetMapping("/workflows/{id}") public WorkflowRun get(@PathVariable UUID id){return service.get(id);}
    @PostMapping("/workflows/{id}/cancel") public WorkflowRun cancel(@PathVariable UUID id){return service.cancel(id);}
}
