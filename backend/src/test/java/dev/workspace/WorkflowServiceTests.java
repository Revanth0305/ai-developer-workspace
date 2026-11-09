package dev.workspace;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:workflow-tests;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","workspace.workflow.enabled=true","workspace.deployment.maintenance=false"})
class WorkflowServiceTests {
    @Autowired WorkflowService service;
    @Autowired WorkflowStore store;
    @Autowired WorkflowRepository workflows;
    @Autowired ExecutionAttemptRepository attempts;
    @Autowired GitPublicationRepository publications;
    @Autowired DeploymentRepository deployments;
    @MockitoBean ExecutionService executions;
    @MockitoBean DockerSandbox runtime;
    UUID task;
    final String image="sha256:"+"a".repeat(64);
    final WorkflowService.Input input=new WorkflowService.Input(List.of("public/greeting.mjs"),List.of(),1);
    AtomicReference<String> context;
    AtomicReference<UUID> latest;
    AtomicInteger checks;
    @BeforeEach void setup()throws Exception {
        workflows.deleteAll();deployments.deleteAll();publications.deleteAll();attempts.deleteAll();task=UUID.randomUUID();context=new AtomicReference<>("context");latest=new AtomicReference<>();checks=new AtomicInteger();
        when(runtime.status(anyBoolean())).thenReturn(new DockerSandbox.Status(true,true,"NODE24_TEST",image,"Ready"));
        when(executions.preview(eq(task),anyList(),anyList(),nullable(UUID.class))).thenAnswer(c->new ExecutionService.Preview(context.get()+(c.getArgument(3)==null?"":"-repair"),"reviewed source and plan",input.files(),input.newFiles(),c.getArgument(3),"commit","source","NODE24_TEST"));
        when(executions.list(task)).thenReturn(List.of());
        when(executions.propose(eq(task),anyList(),anyList(),nullable(UUID.class),anyString())).thenAnswer(c->{var a=new ExecutionAttempt(task,UUID.randomUUID());a.status="REVIEW";a.reviewHash="diff-hash";a.repairOf=c.getArgument(3);attempts.saveAndFlush(a);latest.set(a.id);return a;});
        when(executions.check(any(),eq("diff-hash"),eq(image))).thenAnswer(c->{checks.incrementAndGet();return result(c.getArgument(0),"PASSED",0,3,false);});
        when(executions.cancel(any())).thenAnswer(c->result(c.getArgument(0),"CANCELLED",null,null,false));
    }
    ExecutionAttempt result(UUID id,String status,Integer exit,Integer count,boolean pending){var a=attempts.findById(id).orElseThrow();a.status=status;a.exitCode=exit;a.testCount=count;a.cleanupPending=pending;return attempts.saveAndFlush(a);}
    WorkflowRun start(WorkflowService.Input in)throws Exception{var preview=service.preview(task,in);return service.start(task,in,preview.hash());}
    void eventually(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!condition.getAsBoolean()){if(System.nanoTime()>end)fail("Workflow did not reach its expected state");Thread.sleep(30);}Thread.sleep(60);}
    void done(WorkflowRun r,String status)throws Exception{eventually(()->store.get(r.id).status.equals(status));}
    @Test void successfulLoopRetainsRealCandidateAndTracksSeparateCommitPushDeploymentGates()throws Exception {
        var r=start(input);done(r,"REVIEW_COMMIT");assertThat(store.get(r.id).candidates).isEqualTo(1);assertThat(store.get(r.id).events).contains("Actual checks passed");assertThat(publications.findAll()).isEmpty();assertThat(deployments.findAll()).isEmpty();
        var p=new GitPublication(latest.get());p.status="COMMITTED";p.commitId="a".repeat(40);publications.saveAndFlush(p);assertThat(service.get(r.id).status).isEqualTo("REVIEW_PUSH");
        p.status="PUSH_UNKNOWN";publications.saveAndFlush(p);assertThat(service.get(r.id).status).isEqualTo("REVIEW_PUSH");p.status="PUSHED";publications.saveAndFlush(p);assertThat(service.get(r.id).status).isEqualTo("REVIEW_DEPLOY");
        var d=new Deployment();d.executionId=latest.get();d.status="LIVE";deployments.saveAndFlush(d);assertThat(service.get(r.id).status).isEqualTo("COMPLETED");d.status="EXPIRED";deployments.saveAndFlush(d);assertThat(service.get(r.id).status).isEqualTo("DEPLOYMENT_STOPPED");
    }
    @Test void failedCheckRepairsOnlySamePathsWithinBudget()throws Exception {
        AtomicReference<UUID> failed=new AtomicReference<>();when(executions.check(any(),anyString(),eq(image))).thenAnswer(c->{UUID id=c.getArgument(0);if(checks.incrementAndGet()==1){failed.set(id);return result(id,"FAILED",1,3,false);}return result(id,"PASSED",0,3,false);});
        var r=start(new WorkflowService.Input(input.files(),input.newFiles(),2));done(r,"REVIEW_COMMIT");assertThat(store.get(r.id).candidates).isEqualTo(2);assertThat(attempts.findById(latest.get()).orElseThrow().repairOf).isEqualTo(failed.get());
        verify(executions).propose(task,input.files(),input.newFiles(),failed.get(),"context-repair");assertThat(checks.get()).isEqualTo(2);
    }
    @Test void exhaustedBudgetStopsWithoutFurtherRequests()throws Exception {
        when(executions.check(any(),anyString(),eq(image))).thenAnswer(c->{checks.incrementAndGet();return result(c.getArgument(0),"FAILED",1,3,false);});var r=start(new WorkflowService.Input(input.files(),input.newFiles(),2));done(r,"FAILED");assertThat(checks.get()).isEqualTo(2);assertThat(store.get(r.id).error).contains("budget");
    }
    @Test void infrastructureFailureAndUnconfirmedTestsAreNotAutomaticallyRetried()throws Exception {
        when(executions.check(any(),anyString(),eq(image))).thenAnswer(c->result(c.getArgument(0),"FAILED",null,null,false));var r=start(new WorkflowService.Input(input.files(),input.newFiles(),3));done(r,"FAILED");assertThat(store.get(r.id).candidates).isEqualTo(1);assertThat(store.get(r.id).error).contains("infrastructure");
    }
    @Test void pendingCleanupStopsRepairAndRemainsVisible()throws Exception {
        when(executions.check(any(),anyString(),eq(image))).thenAnswer(c->result(c.getArgument(0),"PASSED",0,3,true));var r=start(input);done(r,"FAILED");assertThat(service.get(r.id).cleanupPending).isTrue();assertThat(store.get(r.id).error).contains("cleanup");
    }
    @Test void sourceOrPlanChangesStopBeforeChecks()throws Exception {
        when(executions.propose(eq(task),anyList(),anyList(),nullable(UUID.class),anyString())).thenAnswer(c->{var a=new ExecutionAttempt(task,UUID.randomUUID());a.status="REVIEW";attempts.saveAndFlush(a);context.set("changed");return a;});var r=start(input);done(r,"FAILED");verify(executions,never()).check(any(),anyString(),anyString());assertThat(store.get(r.id).error).contains("changed");
    }
    @Test void staleReviewRuntimeAndTaskBudgetAreRejected()throws Exception {
        var review=service.preview(task,input);when(runtime.status(anyBoolean())).thenReturn(new DockerSandbox.Status(true,true,"NODE24_TEST","sha256:"+"b".repeat(64),"Changed"));assertThatThrownBy(()->service.start(task,input,review.hash())).hasMessageContaining("changed");
        when(executions.list(task)).thenReturn(List.of(new ExecutionAttempt(task,UUID.randomUUID()),new ExecutionAttempt(task,UUID.randomUUID())));assertThatThrownBy(()->service.preview(task,new WorkflowService.Input(input.files(),List.of(),2))).hasMessageContaining("limit");assertThat(workflows.findAll()).isEmpty();
    }
    void generating()throws Exception{when(executions.propose(eq(task),anyList(),anyList(),nullable(UUID.class),anyString())).thenAnswer(c->{var a=new ExecutionAttempt(task,UUID.randomUUID());attempts.saveAndFlush(a);latest.set(a.id);return a;});}
    @Test void cancellationPreventsChecksAndAnotherWorkflowWhileStopping()throws Exception {
        generating();var r=start(input);eventually(()->latest.get()!=null);assertThatThrownBy(()->start(input)).hasMessageContaining("running");service.cancel(r.id);done(r,"CANCELLED");verify(executions,never()).check(any(),anyString(),anyString());verify(executions,atLeastOnce()).cancel(latest.get());
    }
    @Test void deadlineStopsOwnedWorkWithoutRetry()throws Exception {
        generating();var r=start(input);eventually(()->latest.get()!=null);store.update(r.id,v->v.deadline=Instant.now().minusSeconds(1),null);done(r,"TIMED_OUT");verify(executions,atLeastOnce()).cancel(latest.get());verify(executions,never()).check(any(),anyString(),anyString());
    }
    @Test void restartMarksInterruptedWithoutResumingAndPreservesFinishedRuns() {
        var r=new WorkflowRun();r.taskId=task;r.status="CHECKING";store.save(r);var finished=new WorkflowRun();finished.taskId=task;finished.status="CANCELLED";store.save(finished);service.recover();assertThat(store.get(r.id).status).isEqualTo("INTERRUPTED");assertThat(store.get(finished.id).status).isEqualTo("CANCELLED");assertThat(store.get(r.id).events).contains("Interrupted");verifyNoInteractions(executions);
    }
    @Test void zeroConfirmedTestsCannotPassTheWorkflow()throws Exception {
        when(executions.check(any(),anyString(),eq(image))).thenAnswer(c->result(c.getArgument(0),"PASSED",0,0,false));var r=start(input);done(r,"FAILED");assertThat(service.get(r.id).status).isEqualTo("FAILED");
    }
    @Test void aChangeAfterAdmissionCannotAuthorizeANewContext()throws Exception {
        AtomicInteger calls=new AtomicInteger();when(executions.preview(eq(task),anyList(),anyList(),nullable(UUID.class))).thenAnswer(c->{String value=calls.incrementAndGet()<=2?"context":"unreviewed";return new ExecutionService.Preview(value,"source",input.files(),List.of(),null,"commit","source","NODE24_TEST");});
        var r=start(input);done(r,"FAILED");verify(executions,never()).propose(any(),anyList(),anyList(),nullable(UUID.class),anyString());
    }
    @Test void disabledProfileAndActiveCandidateCannotStartWorkflows()throws Exception {
        var disabled=new WorkflowService(workflows,store,executions,new ExecutionStore(attempts,null,null),runtime,publications,deployments,false);
        assertThatThrownBy(()->disabled.preview(task,input)).hasMessageContaining("disabled");var review=service.preview(task,input);when(executions.busy()).thenReturn(true);assertThatThrownBy(()->service.start(task,input,review.hash())).hasMessageContaining("cleanup");assertThat(workflows.findAll()).isEmpty();
    }
}
