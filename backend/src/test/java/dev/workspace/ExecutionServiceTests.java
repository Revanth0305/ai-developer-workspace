package dev.workspace;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:execution-tests;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop"})
class ExecutionServiceTests {
    static final Path root;
    static {try {root=Files.createTempDirectory("execution-tests-");Files.createDirectory(root.resolve("platform"));}catch(Exception e){throw new RuntimeException(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("workspace.platform-root",()->root.resolve("platform").toString());}
    @Autowired ExecutionService service;
    @Autowired ExecutionAttemptRepository attempts;
    @Autowired ProjectRepository projects;
    @Autowired TaskRepository tasks;
    @Autowired PlanAttemptRepository plans;
    @Autowired ActivityRepository activity;
    @Autowired CandidateWorkspace workspaces;
    @MockitoBean DockerSandbox sandbox;
    @MockitoBean CodexPlanner codex;
    @MockitoBean PlanningClientFactory clients;
    Project project;Task task;PlanAttempt plan;PlanningClient client;
    final String image="sha256:"+"a".repeat(64);
    static final List<String> FILES=List.of("src/greeting.js"),ADDED=List.of("test/greeting.test.js");
    static final String PROPOSAL="{\"summary\":\"Add validation and tests\",\"changes\":[{\"path\":\"src/greeting.js\",\"operation\":\"write\",\"content\":\"export function greeting(name) { if (!name.trim()) throw new Error('Blank name'); return `Hello, ${name}`; }\\n\"},{\"path\":\"test/greeting.test.js\",\"operation\":\"write\",\"content\":\"import test from 'node:test'; import assert from 'node:assert/strict'; import { greeting } from '../src/greeting.js'; test('blank', () => assert.throws(() => greeting(' ')));\\n\"}]}";
    @BeforeEach void setup() throws Exception {
        activity.deleteAll();attempts.deleteAll();plans.deleteAll();tasks.deleteAll();projects.deleteAll();
        Path source=Files.createTempDirectory(root,"source-");Files.createDirectories(source.resolve("src"));
        Files.writeString(source.resolve("src/greeting.js"),"export function greeting(name) { return `Hello, ${name}`; }\n");
        Files.writeString(source.resolve("package.json"),"{\"type\":\"module\"}\n");
        workspaces.git(source,"init","--quiet");workspaces.git(source,"add","--all");workspaces.git(source,"-c","user.name=Fixture","-c","user.email=fixture@localhost","-c","commit.gpgSign=false","commit","--quiet","-m","Initial source");
        project=projects.save(new Project("Fixture",source.toString(),"No dependencies"));
        task=tasks.save(new Task(project.id,"Add greeting validation and tests"));task.status="PLANNED";tasks.save(task);
        plan=new PlanAttempt(task.id,"Reviewed context");plan.status="SUCCEEDED";plan.result="Validate blank names in src/greeting.js and add test/greeting.test.js.";plans.save(plan);
        when(sandbox.status(anyBoolean())).thenReturn(new DockerSandbox.Status(true,true,"NODE24_TEST",image,"Ready"));
        when(sandbox.enabled()).thenReturn(true);
        when(sandbox.cleanup(any())).thenReturn(true);
        when(sandbox.check(any(),any(),eq(image),any(),any())).thenReturn(new DockerSandbox.CheckResult(0,"# tests 1\n# pass 1\n# fail 0\n",1));
        when(codex.status()).thenReturn(new CodexPlanner.ConnectionStatus(true,true,"chatgpt","Ready"));
        client=mock(PlanningClient.class);when(client.propose(anyString(),any(),any())).thenReturn(PROPOSAL);
        when(clients.create(anyString(),any())).thenReturn(client);
    }
    void eventually(BooleanSupplier condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(!condition.getAsBoolean()){if(System.nanoTime()>until)fail("Timed out waiting for execution state");Thread.sleep(25);}
    }
    ExecutionAttempt propose(UUID repair) throws Exception {
        var context=service.preview(task.id,FILES,ADDED,repair);
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(true){try{return service.propose(task.id,FILES,ADDED,repair,context.hash());}catch(org.springframework.web.server.ResponseStatusException e){if(!e.getReason().contains("running or stopping")||System.nanoTime()>until)throw e;Thread.sleep(25);}}
    }
    ExecutionAttempt reviewed() throws Exception {
        ExecutionAttempt attempt=propose(null);eventually(()->attempts.findById(attempt.id).orElseThrow().status.equals("REVIEW"));return attempts.findById(attempt.id).orElseThrow();
    }
    void check(ExecutionAttempt attempt) throws Exception {service.check(attempt.id,attempt.reviewHash,image);}
    ExecutionAttempt stored(UUID id){return attempts.findById(id).orElseThrow();}
    @Test void successfulCandidateHasRealCheckResultPatchAndUnchangedSource() throws Exception {
        String original=workspaces.inspect(project).fingerprint();ExecutionAttempt attempt=reviewed();check(attempt);
        eventually(()->stored(attempt.id).status.equals("PASSED"));
        assertThat(stored(attempt.id).exitCode).isZero();assertThat(stored(attempt.id).testCount).isEqualTo(1);
        assertThat(service.patch(attempt.id)).contains("Blank name","test/greeting.test.js");
        assertThat(workspaces.inspect(project).fingerprint()).isEqualTo(original);
        assertThat(stored(attempt.id).cleanupPending).isFalse();
    }
    @Test void contextAndCandidateTamperingAreRejectedBeforeProviderOrRunnerUse() throws Exception {
        var context=service.preview(task.id,FILES,ADDED,null);
        assertThatThrownBy(()->service.propose(task.id,FILES,ADDED,null,"0".repeat(64))).hasMessageContaining("context changed");
        verify(clients,never()).create(anyString(),any());
        ExecutionAttempt attempt=reviewed();
        Files.writeString(root.resolve("platform/.runtime/executions/"+attempt.id+"/candidate/src/greeting.js"),"tampered");
        assertThatThrownBy(()->check(attempt)).hasMessageContaining("candidate changed");
        verify(sandbox,never()).check(any(),any(),anyString(),any(),any());
    }
    @Test void failedChecksCanBeRepairedOnlyAfterReviewAndWithinTheCandidateLimit() throws Exception {
        when(sandbox.check(any(),any(),eq(image),any(),any())).thenReturn(new DockerSandbox.CheckResult(1,"# tests 1\n# fail 1\npassword=fixture-value\n",1));
        ExecutionAttempt first=reviewed();check(first);eventually(()->stored(first.id).status.equals("FAILED"));
        var repair=service.preview(task.id,FILES,ADDED,first.id);
        assertThat(repair.prompt()).contains("ACTUAL FAILED CHECK LOGS","[REDACTED]").doesNotContain("fixture-value");
        when(sandbox.check(any(),any(),eq(image),any(),any())).thenReturn(new DockerSandbox.CheckResult(0,"# tests 1\n# pass 1\n",1));
        ExecutionAttempt second=propose(first.id);eventually(()->stored(second.id).status.equals("REVIEW"));check(stored(second.id));eventually(()->stored(second.id).status.equals("PASSED"));
        assertThat(stored(second.id).repairOf).isEqualTo(first.id);
        ExecutionAttempt third=propose(null);eventually(()->stored(third.id).status.equals("REVIEW"));
        assertThatThrownBy(()->propose(null)).hasMessageContaining("three candidate proposals");
    }
    @Test void checkRetriesAreBoundedAndZeroTestsCannotBeCalledSuccess() throws Exception {
        when(sandbox.check(any(),any(),eq(image),any(),any())).thenReturn(new DockerSandbox.CheckResult(0,"# tests 0\n",0));
        ExecutionAttempt attempt=reviewed();
        for(int i=0;i<3;i++){check(stored(attempt.id));eventually(()->stored(attempt.id).status.equals("FAILED"));}
        assertThat(stored(attempt.id).checkRuns).isEqualTo(3);
        assertThat(stored(attempt.id).error).contains("No tests");
        assertThatThrownBy(()->check(stored(attempt.id))).hasMessageContaining("three-run");
        assertThatThrownBy(()->service.patch(attempt.id)).hasMessageContaining("successfully checked");
    }
    @Test void cleanupFailureBlocksExportAndNewWorkUntilOwnedResourcesAreRemoved() throws Exception {
        when(sandbox.cleanup(any())).thenReturn(false);
        ExecutionAttempt attempt=reviewed();check(attempt);eventually(()->stored(attempt.id).status.equals("PASSED"));
        assertThat(stored(attempt.id).cleanupPending).isTrue();
        assertThatThrownBy(()->service.patch(attempt.id)).hasMessageContaining("cleaned-up");
        assertThatThrownBy(()->propose(null)).hasMessageContaining("cleanup is pending");
        when(sandbox.cleanup(any())).thenReturn(true);service.cleanup(attempt.id);
        assertThat(service.patch(attempt.id)).contains("Blank name");
    }
    @Test void cancellationBeforeProviderConnectionPreventsAnyModelRequest() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(clients.create(anyString(),any())).thenAnswer(inv->{entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();return client;});
        ExecutionAttempt attempt=propose(null);assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();service.cancel(attempt.id);release.countDown();
        verify(client,timeout(3000)).close();verify(client,never()).initialize();verify(client,never()).propose(anyString(),any(),any());
        assertThat(stored(attempt.id).status).isEqualTo("CANCELLED");
    }
    @Test void cancellationDuringChecksDiscardsLateSuccessAndStillCleansTheRunner() throws Exception {
        CountDownLatch running=new CountDownLatch(1);
        when(sandbox.check(any(),any(),eq(image),any(),any())).thenAnswer(inv->{running.countDown();BooleanSupplier cancelled=inv.getArgument(3);long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!cancelled.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(10);return new DockerSandbox.CheckResult(0,"# tests 1",1);});
        ExecutionAttempt attempt=reviewed();check(attempt);assertThat(running.await(5,TimeUnit.SECONDS)).isTrue();service.cancel(attempt.id);
        eventually(()->!stored(attempt.id).cleanupPending);assertThat(stored(attempt.id).status).isEqualTo("CANCELLED");assertThat(stored(attempt.id).exitCode).isNull();
        verify(sandbox,timeout(3000)).cleanup(attempt.id);
    }
    @Test void failedCreationAndRestartRecoveryDoNotLoseTheExistingPlan() throws Exception {
        when(clients.create(anyString(),any())).thenThrow(new java.io.IOException("fixture startup failure"));
        ExecutionAttempt failed=propose(null);eventually(()->stored(failed.id).status.equals("FAILED"));
        doReturn(client).when(clients).create(anyString(),any());
        ExecutionAttempt next=propose(null);eventually(()->stored(next.id).status.equals("REVIEW"));
        ExecutionAttempt orphan=new ExecutionAttempt(task.id,plan.id);orphan.status="CHECKING";orphan.cleanupPending=true;attempts.save(orphan);
        service.recover();assertThat(stored(orphan.id).status).isEqualTo("INTERRUPTED");assertThat(stored(orphan.id).cleanupPending).isFalse();
        assertThat(plans.findById(plan.id).orElseThrow().result).contains("Validate blank names");
        assertThat(stored(next.id).status).isEqualTo("REVIEW");
    }
}
