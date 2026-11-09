package dev.workspace;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.annotation.PreDestroy;
import java.nio.file.*;
import java.time.*;
import java.util.*;

@Service
public class DeploymentService {
    private final DeploymentRepository deployments;
    private final DeploymentStore store;
    private final GitPublicationRepository publications;
    private final ExecutionStore executions;
    private final DeploymentArtifact artifacts;
    private final DockerDeployment docker;
    private final DockerSandbox runtime;
    private final boolean enabled,maintenance;
    private final Path root;
    private Run active;
    private boolean stopping;
    private static class Run {final UUID id;volatile boolean cancelled;Run(UUID id){this.id=id;}}
    public DeploymentService(DeploymentRepository deployments,DeploymentStore store,GitPublicationRepository publications,ExecutionStore executions,
            DeploymentArtifact artifacts,DockerDeployment docker,DockerSandbox runtime,@Value("${workspace.platform-root}") String platform,
            @Value("${workspace.deployment.enabled:false}") boolean enabled,@Value("${workspace.deployment.maintenance:true}") boolean maintenance)throws Exception {
        this.deployments=deployments;this.store=store;this.publications=publications;this.executions=executions;this.artifacts=artifacts;this.docker=docker;this.runtime=runtime;
        this.enabled=enabled;this.maintenance=maintenance;root=Path.of(platform).toRealPath().resolve(".runtime/deployments");Files.createDirectories(root);
    }
    public boolean enabled(){return enabled;}
    public List<Deployment> list(UUID executionId){executions.get(executionId);return deployments.findByExecutionIdOrderByCreatedAtDesc(executionId);}
    public List<Deployment> listProject(UUID projectId){return deployments.findByProjectIdOrderByCreatedAtDesc(projectId);}
    private void enabledOrThrow(){if(!enabled)throw WorkspaceFiles.bad("Local test deployment is disabled for this session.");}
    private GitPublication eligible(UUID id) {
        var execution=executions.get(id);
        if(!execution.status.equals("PASSED") || execution.cleanupPending || execution.exitCode==null || execution.exitCode!=0 || execution.testCount==null || execution.testCount<1)
            throw WorkspaceFiles.bad("A passing, cleaned-up candidate is required for deployment.");
        GitPublication p=publications.findById(id).orElseThrow(()->WorkspaceFiles.bad("Commit the checked candidate locally before deployment."));
        if(!Set.of("COMMITTED","PUSHED","PUSH_UNKNOWN").contains(p.status) || p.commitId.isBlank()) throw WorkspaceFiles.bad("A finished private candidate commit is required.");
        return p;
    }
    private String hash(Deployment d)throws Exception{return CandidateWorkspace.fingerprint(d.id.toString(),Map.of("commit",d.commitId,"artifact",d.artifactHash,"image",d.imageId,"manifest",d.manifest,"policy","LOCAL_STATIC_NODE24-v1;public;127.0.0.1:random;1800seconds;no-user-secrets"));}
    public synchronized Deployment preview(UUID executionId)throws Exception {
        enabledOrThrow();GitPublication p=eligible(executionId);var snapshot=artifacts.read(p);var status=runtime.status(true);
        if(!status.available()) throw WorkspaceFiles.bad(status.message());
        List<Deployment> history=list(executionId);Deployment d=history.stream().filter(value->value.status.equals("REVIEW")).findFirst().orElse(null);
        if(d==null){if(history.size()>=3)throw WorkspaceFiles.bad("This candidate has reached its three-deployment limit.");d=new Deployment();}
        d.executionId=executionId;d.projectId=p.projectId;d.taskId=executions.get(executionId).taskId;d.commitId=p.commitId;d.artifactHash=snapshot.hash();
        d.imageId=status.imageId();d.manifest=snapshot.manifest();d.reviewHash=hash(d);return store.save(d,null);
    }
    public synchronized Deployment start(UUID id,String reviewedHash)throws Exception {
        enabledOrThrow();
        if(stopping || active!=null || deployments.findAll().stream().anyMatch(d->d.cleanupPending || Set.of("LIVE","DEPLOYING","STOPPING").contains(d.status)))
            throw WorkspaceFiles.bad("Stop the current deployment or finish owned-resource cleanup before starting another.");
        Deployment d=store.get(id);
        if(!d.status.equals("REVIEW") || !d.reviewHash.equals(reviewedHash) || !hash(d).equals(reviewedHash)) throw WorkspaceFiles.bad("Review the exact deployment request again.");
        GitPublication p=eligible(d.executionId);var snapshot=artifacts.read(p);var status=runtime.status(true);
        if(!d.commitId.equals(p.commitId)||!snapshot.hash().equals(d.artifactHash)||!snapshot.manifest().equals(d.manifest))throw WorkspaceFiles.bad("The committed artifact changed. Review a fresh deployment request.");
        if(!status.available()||!status.imageId().equals(d.imageId))throw WorkspaceFiles.bad("The Docker runtime or image changed. Review a fresh deployment request.");
        d.status="DEPLOYING";d.expiresAt=Instant.now().plusSeconds(1800).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);d.cleanupPending=true;d.logs="Preparing the reviewed static artifact.\n";d.error="";
        Deployment saved=store.save(d,"Reviewed local test deployment started for commit "+d.commitId);
        Run run=new Run(id);active=run;Thread.ofVirtual().name("local-deployment").start(()->launch(run,saved,snapshot));return saved;
    }
    private void launch(Run run,Deployment d,DeploymentArtifact.Snapshot snapshot) {
        boolean live=false;
        try {
            if(run.cancelled) return;
            Path staging=root.resolve(d.id.toString());artifacts.stage(d,snapshot,staging);
            int port=docker.launch(d,staging,()->run.cancelled||stopping);
            synchronized(this) {
                if(!run.cancelled&&!stopping){store.update(d.id,value->{value.port=port;value.url="http://127.0.0.1:"+port+"/";value.status="LIVE";value.cleanupPending=false;value.logs=docker.logs(d.id);},"Local test deployment passed HTTP health verification: http://127.0.0.1:"+port+"/");live=true;active=null;}
            }
        } catch(Exception e) {
            synchronized(this){if(!run.cancelled&&!stopping)store.update(d.id,value->{value.status="FAILED";value.error="The local deployment did not pass its startup and HTTP checks. Review logs and Docker availability before retrying.";},"Local test deployment failed; no successful URL was reported");}
        } finally {
            if(!live) {
                String logs=docker.logs(d.id);boolean clean=docker.cleanup(d.id);
                synchronized(this){if(!stopping)store.update(d.id,value->{value.logs=logs;value.cleanupPending=!clean;if(!clean){value.terminalStatus=run.cancelled?"CANCELLED":"FAILED";value.status="CLEANUP_PENDING";value.error="Owned deployment cleanup is pending. Retry cleanup before starting another deployment.";}},run.cancelled?(clean?"Local test deployment cancelled":"Deployment cancellation requested; owned cleanup is pending"):null);}
            }
            synchronized(this){if(active==run)active=null;}
        }
    }
    public synchronized Deployment stop(UUID id) {
        Deployment d=store.get(id);
        if(active!=null&&active.id.equals(id)&&Set.of("DEPLOYING","CANCELLED").contains(d.status)){active.cancelled=true;return store.update(id,value->value.status="CANCELLED","Local deployment cancellation requested");}
        if(d.status.equals("REVIEW"))return store.update(id,value->value.status="CANCELLED",null);
        if(!d.status.equals("LIVE")&&!d.cleanupPending)throw WorkspaceFiles.bad("This deployment is already stopped.");
        return remove(d,"STOPPED","Local test deployment stopped");
    }
    public synchronized Deployment cleanup(UUID id) {
        if(active!=null)throw WorkspaceFiles.bad("Wait for the running operation to finish stopping.");
        Deployment d=store.get(id);if(!d.cleanupPending)return d;
        return remove(d,d.terminalStatus==null||d.terminalStatus.isBlank()?d.status:d.terminalStatus,"Owned deployment cleanup retried");
    }
    private Deployment remove(Deployment d,String status,String message) {
        String logs=docker.logs(d.id);store.update(d.id,value->{value.status="STOPPING";value.cleanupPending=true;},null);
        boolean clean=docker.cleanup(d.id);
        return store.update(d.id,value->{value.terminalStatus=status;value.status=clean?status:"CLEANUP_PENDING";value.cleanupPending=!clean;value.logs=logs;value.url="";if(clean)value.error="";else value.error="Owned deployment cleanup is pending. Retry cleanup before another deployment.";},clean?message:"Owned deployment cleanup is pending");
    }
    public synchronized Deployment refresh(UUID id) {
        Deployment d=store.get(id);
        if(!d.status.equals("LIVE"))return d;
        if(d.expiresAt==null||!d.expiresAt.isAfter(Instant.now()))return remove(d,"EXPIRED","Local deployment lease expired and owned resources were removed");
        if(!docker.verifyLive(d)) {
            Deployment failed=remove(d,"FAILED","Local deployment health could not be verified; owned resources cleanup requested");
            return store.update(failed.id,value->value.error=failed.cleanupPending?failed.error:"The deployment stopped or failed its ownership/isolation/HTTP health checks. Review logs before starting a fresh deployment.",null);
        }
        return store.update(id,value->value.logs=docker.logs(id),null);
    }
    @Scheduled(fixedDelay=15000,initialDelay=15000) public void maintain() {
        if(!enabled||!maintenance||stopping)return;
        for(Deployment d:deployments.findAll())if(d.status.equals("LIVE"))try{refresh(d.id);}catch(Exception ignored){}
    }
    @EventListener(ApplicationReadyEvent.class) public synchronized void recover() {
        for(Deployment d:deployments.findAll()) {
            if(Set.of("DEPLOYING","STOPPING").contains(d.status)||d.cleanupPending)remove(d,"INTERRUPTED","Interrupted local deployment recovered with owned-resource cleanup");
            else if(d.status.equals("LIVE")){if(enabled)refresh(d.id);else remove(d,"STOPPED","Local deployment stopped because the deployment profile is disabled");}
        }
    }
    @PreDestroy public synchronized void shutdown(){stopping=true;if(active!=null)active.cancelled=true;}
}
