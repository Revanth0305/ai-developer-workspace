package dev.workspace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.Instant;
import java.util.function.Consumer;
@Service
public class DeploymentStore {
    private final DeploymentRepository deployments;
    private final ActivityRepository activity;
    public DeploymentStore(DeploymentRepository deployments,ActivityRepository activity){this.deployments=deployments;this.activity=activity;}
    public Deployment get(UUID id){return deployments.findById(id).orElseThrow(()->WorkspaceFiles.bad("Deployment not found."));}
    @Transactional public Deployment save(Deployment d,String message){d.updatedAt=Instant.now();Deployment saved=deployments.saveAndFlush(d);if(message!=null)activity.save(new Activity(d.projectId,d.taskId,message));return saved;}
    @Transactional public Deployment update(UUID id,Consumer<Deployment> change,String message){Deployment d=get(id);change.accept(d);return save(d,message);}
}
