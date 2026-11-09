package dev.workspace;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import tools.jackson.databind.json.JsonMapper;

/** Bounded preauthorized candidate loop; publication and deployment keep their exact review gates. */
@Service
public class WorkflowService {
    public record Input(List<String> files,List<String> newFiles,int maxCandidates){}
    public record Preview(String hash,String contextHash,String prompt,String policy,String imageId,Input input){}
    private static final String POLICY="NODE24_WORKFLOW-v1: up to 3 candidates, one fixed isolated check per candidate, 15-minute wall-clock limit, one workflow at a time. Selected paths and source/plan context stay fixed. Failed nonzero-check logs (redacted, at most 8000 characters) and prior diffs may be sent to Codex for repairs within the same authorization. No infrastructure retries, original-folder edits, automatic commits, pushes or deployments. Passing output stops for exact diff/commit review; publication and deployment retain separate confirmations. Interrupted runs never resume automatically.";
    private static final Set<String> ACTIVE=Set.of("PROPOSING","CHECKING","STOPPING");
    private final WorkflowRepository repository;
    private final WorkflowStore store;
    private final ExecutionService executions;
    private final ExecutionStore attempts;
    private final DockerSandbox runtime;
    private final GitPublicationRepository publications;
    private final DeploymentRepository deployments;
    private final boolean enabled;
    private final JsonMapper json=JsonMapper.builder().build();
    private Run active;
    private boolean stopping;
    private static class Run {final UUID id;volatile boolean cancelled;Run(UUID id){this.id=id;}}
    public WorkflowService(WorkflowRepository repository,WorkflowStore store,ExecutionService executions,ExecutionStore attempts,DockerSandbox runtime,
            GitPublicationRepository publications,DeploymentRepository deployments,@Value("${workspace.workflow.enabled:false}") boolean enabled){
        this.repository=repository;this.store=store;this.executions=executions;this.attempts=attempts;this.runtime=runtime;this.publications=publications;this.deployments=deployments;this.enabled=enabled;
    }
    public boolean enabled(){return enabled;}
    private void enabledOrThrow(){if(!enabled)throw WorkspaceFiles.bad("Controlled workflows are disabled for this session.");}
    public Preview preview(UUID taskId,Input input)throws Exception {
        enabledOrThrow();if(input==null||input.maxCandidates()<1||input.maxCandidates()>3)throw WorkspaceFiles.bad("Choose one to three candidates.");
        var context=executions.preview(taskId,input.files(),input.newFiles(),null);
        int used=executions.list(taskId).size();if(used+input.maxCandidates()>3)throw WorkspaceFiles.bad("The requested budget exceeds this task's three-candidate limit.");
        var status=runtime.status(true);if(!status.available())throw WorkspaceFiles.bad(status.message());
        Input normalized=new Input(context.files(),context.newFiles(),input.maxCandidates());
        String hash=CandidateWorkspace.fingerprint(context.hash(),Map.of("policy",POLICY,"image",status.imageId(),"budget",String.valueOf(input.maxCandidates()),"used",String.valueOf(used)));
        return new Preview(hash,context.hash(),context.prompt(),POLICY,status.imageId(),normalized);
    }
    public synchronized WorkflowRun start(UUID taskId,Input input,String hash)throws Exception {
        enabledOrThrow();if(stopping||active!=null||repository.findAll().stream().anyMatch(r->ACTIVE.contains(r.status)))throw WorkspaceFiles.bad("Another workflow is running or stopping.");
        if(executions.busy()||executions.list(taskId).stream().anyMatch(a->a.cleanupPending))throw WorkspaceFiles.bad("Wait for candidate work and owned cleanup to finish before starting a workflow.");
        Preview review=preview(taskId,input);if(!review.hash().equals(hash))throw WorkspaceFiles.bad("The workflow request changed. Review it again.");
        WorkflowRun r=new WorkflowRun();r.taskId=taskId;r.maxCandidates=input.maxCandidates();r.imageId=review.imageId();r.contextHash=review.contextHash();r.reviewedContext=review.prompt()+"\n\n"+POLICY;
        r.selection=json.writeValueAsString(review.input());r.deadline=Instant.now().plusSeconds(900);store.save(r);
        Run run=new Run(r.id);active=run;Thread.ofVirtual().name("controlled-workflow").start(()->execute(run,review.input()));return r;
    }
    private void guard(Run run)throws Exception {
        if(run.cancelled||stopping)throw new CancellationException();
        if(!store.get(run.id).deadline.isAfter(Instant.now()))throw new TimeoutException();
    }
    private void unchanged(WorkflowRun r,Input input)throws Exception {
        if(!executions.preview(r.taskId,input.files(),input.newFiles(),null).hash().equals(r.contextHash))throw WorkspaceFiles.bad("The approved source, instruction or plan changed. Start a newly reviewed workflow.");
        var status=runtime.status(true);if(!status.available()||!status.imageId().equals(r.imageId))throw WorkspaceFiles.bad("The reviewed runtime changed or became unavailable.");
    }
    private ExecutionAttempt waitFor(Run run,UUID child)throws Exception {
        while(true){guard(run);var a=attempts.get(child);if(!Set.of("GENERATING","CHECKING").contains(a.status)&&!executions.busy())return a;Thread.sleep(250);}
    }
    private void execute(Run run,Input input){
        try {
            UUID repair=null;
            for(int i=0;i<input.maxCandidates();i++) {
                WorkflowRun r=store.get(run.id);guard(run);unchanged(r,input);
                var context=executions.preview(r.taskId,input.files(),input.newFiles(),repair);
                ExecutionAttempt child;
                synchronized(this){guard(run);child=executions.propose(r.taskId,input.files(),input.newFiles(),repair,context.hash());store.update(run.id,v->{v.executionId=child.id;v.candidates++;v.status="PROPOSING";},"Candidate "+(i+1)+" requested within the reviewed budget.");}
                var proposed=waitFor(run,child.id);if(!proposed.status.equals("REVIEW"))throw WorkspaceFiles.bad("Candidate preparation stopped without reviewable changes. Inspect its saved result.");
                guard(run);unchanged(store.get(run.id),input);
                synchronized(this){guard(run);executions.check(child.id,proposed.reviewHash,r.imageId);store.update(run.id,v->v.status="CHECKING","Running the fixed reviewed Node.js checks.");}
                var checked=waitFor(run,child.id);
                if(checked.cleanupPending)throw WorkspaceFiles.bad("Owned runner cleanup is pending; resolve it in candidate history.");
                if(checked.status.equals("PASSED")&&checked.exitCode!=null&&checked.exitCode==0&&checked.testCount!=null&&checked.testCount>0){
                    synchronized(this){guard(run);store.update(run.id,v->v.status="REVIEW_COMMIT","Actual checks passed. Review the final diff before committing; no publishing was performed.");}return;
                }
                if(!checked.status.equals("FAILED")||checked.exitCode==null||checked.exitCode==0)throw WorkspaceFiles.bad("Checks did not confirm an eligible test failure. No automatic infrastructure retry was performed.");
                repair=child.id;
                if(i+1==input.maxCandidates())throw WorkspaceFiles.bad("The reviewed candidate budget is exhausted. Inspect the actual failed checks.");
                guard(run);store.update(run.id,v->v.status="PROPOSING","Checks failed. Preparing a bounded repair using the authorized diff and redacted logs.");
            }
        }catch(Exception e){
            cancelChild(run.id);
            synchronized(this){if(!stopping)store.update(run.id,v->{v.status=run.cancelled?"CANCELLED":e instanceof TimeoutException?"TIMED_OUT":"FAILED";v.error=run.cancelled?"Workflow cancelled. Inspect candidate cleanup state before continuing.":e instanceof TimeoutException?"The 15-minute workflow deadline was reached.":e instanceof org.springframework.web.server.ResponseStatusException s?s.getReason():"Workflow stopped. Inspect retained candidate results and runtime availability.";},"Workflow stopped; no automatic commit, push or deployment.");}
        }finally{synchronized(this){if(active==run)active=null;}}
    }
    private void cancelChild(UUID id){
        var r=store.get(id);if(r.executionId!=null)try{var a=attempts.get(r.executionId);if(Set.of("GENERATING","REVIEW","CHECKING").contains(a.status))executions.cancel(a.id);}catch(Exception ignored){}
    }
    public WorkflowRun cancel(UUID id){
        synchronized(this){if(active==null||!active.id.equals(id))throw WorkspaceFiles.bad("This workflow is not running.");active.cancelled=true;store.update(id,v->v.status="STOPPING","Cancellation requested; waiting for the owned candidate operation to stop.");}
        cancelChild(id);return store.get(id);
    }
    public List<WorkflowRun> list(UUID taskId){executions.list(taskId);return repository.findByTaskIdOrderByCreatedAtDesc(taskId).stream().map(r->get(r.id)).toList();}
    public synchronized WorkflowRun get(UUID id){
        WorkflowRun r=store.get(id);
        if(r.executionId!=null){
            r.cleanupPending=attempts.get(r.executionId).cleanupPending;
            if(Set.of("REVIEW_COMMIT","REVIEW_PUSH","REVIEW_DEPLOY","COMPLETED","DEPLOYMENT_STOPPED","DEPLOYMENT_FAILED").contains(r.status)) {
                var p=publications.findById(r.executionId).orElse(null);
                r.status=p==null||!Set.of("COMMITTED","PUSHED","PUSH_UNKNOWN").contains(p.status)?"REVIEW_COMMIT":p.status.equals("PUSHED")?"REVIEW_DEPLOY":"REVIEW_PUSH";
                var ds=deployments.findByExecutionIdOrderByCreatedAtDesc(r.executionId);
                if(!ds.isEmpty()){var d=ds.get(0);r.status=d.status.equals("LIVE")?"COMPLETED":Set.of("STOPPED","EXPIRED","CANCELLED").contains(d.status)?"DEPLOYMENT_STOPPED":Set.of("FAILED","INTERRUPTED","CLEANUP_PENDING").contains(d.status)?"DEPLOYMENT_FAILED":"REVIEW_DEPLOY";}
            }
        }return r;
    }
    @EventListener(ApplicationReadyEvent.class) public synchronized void recover(){for(var r:repository.findAll())if(ACTIVE.contains(r.status))store.update(r.id,v->{v.status="INTERRUPTED";v.error="The backend restarted. Review saved candidate results and cleanup; this workflow will not resume automatically.";},"Interrupted workflow retained for review.");}
    @PreDestroy public void shutdown(){UUID id;synchronized(this){stopping=true;if(active==null)return;active.cancelled=true;id=active.id;}cancelChild(id);}
}
