package dev.workspace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
@Service
public class WorkflowStore {
    private final WorkflowRepository repository;
    public WorkflowStore(WorkflowRepository repository){this.repository=repository;}
    public WorkflowRun get(UUID id){return repository.findById(id).orElseThrow(()->WorkspaceFiles.bad("Workflow not found."));}
    @Transactional public WorkflowRun save(WorkflowRun run){return repository.saveAndFlush(run);}
    @Transactional public WorkflowRun update(UUID id,Consumer<WorkflowRun> change,String event){
        WorkflowRun run=get(id);change.accept(run);run.updatedAt=Instant.now();
        if(event!=null && run.events.length()<24000)run.events+=run.updatedAt+" · "+event+"\n";
        return repository.saveAndFlush(run);
    }
}
