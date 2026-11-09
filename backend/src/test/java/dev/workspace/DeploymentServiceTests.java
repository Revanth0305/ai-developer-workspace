package dev.workspace;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:deployment-tests;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","workspace.git.enabled=true","workspace.deployment.enabled=true","workspace.deployment.maintenance=false"})
class DeploymentServiceTests {
    static final Path root;
    static{try{root=Files.createTempDirectory("deployment-tests-");Files.createDirectory(root.resolve("platform"));}catch(Exception e){throw new RuntimeException(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("workspace.platform-root",()->root.resolve("platform").toString());}
    @Autowired DeploymentService service;@Autowired DeploymentStore store;@Autowired DeploymentRepository deployments;
    @Autowired GitPublishing publishing;@Autowired GitPublicationRepository publications;@Autowired ExecutionAttemptRepository attempts;
    @Autowired ProjectRepository projects;@Autowired TaskRepository tasks;@Autowired CandidateWorkspace git;
    @MockitoBean DockerDeployment docker;@MockitoBean DockerSandbox runtime;@MockitoBean GitHubTransport github;
    ExecutionAttempt execution;Project project;Path source;
    final String image="sha256:"+"a".repeat(64);
    @BeforeEach void setup()throws Exception {
        deployments.deleteAll();publications.deleteAll();attempts.deleteAll();tasks.deleteAll();projects.deleteAll();
        source=Files.createTempDirectory(root,"source-");Files.createDirectory(source.resolve("public"));
        Files.writeString(source.resolve("public/index.html"),"<!doctype html><title>Fixture</title><script src=\"app.js\"></script>\n");
        Files.writeString(source.resolve("public/app.js"),"console.log('before');\n");git.git(source,"init","--quiet");git.git(source,"add","--all");git.git(source,"-c","user.name=Fixture","-c","user.email=fixture@localhost","-c","commit.gpgSign=false","commit","--quiet","-m","Baseline");
        project=projects.save(new Project("Fixture",source.toString(),""));Task task=tasks.save(new Task(project.id,"Update static fixture"));
        var snapshot=git.inspect(project);execution=new ExecutionAttempt(task.id,UUID.randomUUID());execution.status="PASSED";execution.exitCode=0;execution.testCount=1;execution.sourceFingerprint=snapshot.fingerprint();execution.baseCommit=snapshot.commit();
        Path candidate=root.resolve("platform/.runtime/executions").resolve(execution.id.toString()).resolve("candidate");git.create(candidate,snapshot);git.apply(candidate,List.of(new ChangeDocument.Change("public/app.js","write","console.log('after');\n")));
        execution.diff=git.diff(candidate);execution.candidateFingerprint=git.candidateHash(candidate);execution.selectedPaths="{\"files\":[\"public/app.js\"],\"newFiles\":[]}";
        execution.proposal="{\"summary\":\"Update fixture\",\"changes\":[{\"path\":\"public/app.js\",\"operation\":\"write\",\"content\":\"console.log('after');\\n\"}]}";attempts.save(execution);
        var preview=publishing.prepare(execution.id,new GitPublishing.Input("FixtureUser","fixture","Fixture","fixture@localhost","Checked static update"));publishing.commit(execution.id,preview.reviewHash);
        when(runtime.status(anyBoolean())).thenReturn(new DockerSandbox.Status(true,true,"NODE24_TEST",image,"Ready"));
        when(docker.launch(any(),any(),any())).thenReturn(49123);when(docker.cleanup(any())).thenReturn(true);when(docker.logs(any())).thenReturn("Static deployment ready\nHTTP 200\n");when(docker.verifyLive(any())).thenReturn(true);
    }
    void eventually(BooleanSupplier check)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(!check.getAsBoolean()){if(System.nanoTime()>end)fail("Deployment did not finish");Thread.sleep(30);}}
    Deployment live()throws Exception{var review=service.preview(execution.id);service.start(review.id,review.reviewHash);eventually(()->store.get(review.id).status.equals("LIVE"));return store.get(review.id);}
    @Test void reviewedCommitDeploysAndStopsWithoutChangingOriginalSource()throws Exception {
        var d=live();var launched=org.mockito.ArgumentCaptor.forClass(Deployment.class);verify(docker).launch(launched.capture(),any(),any());assertThat(d.expiresAt).isEqualTo(launched.getValue().expiresAt);assertThat(d.url).isEqualTo("http://127.0.0.1:49123/");assertThat(d.manifest).contains("index.html","app.js");assertThat(d.cleanupPending).isFalse();assertThat(service.listProject(project.id)).hasSize(1);assertThat(service.listProject(UUID.randomUUID())).isEmpty();
        assertThat(service.refresh(d.id).status).isEqualTo("LIVE");assertThat(service.stop(d.id).status).isEqualTo("STOPPED");assertThat(store.get(d.id).url).isBlank();assertThat(Files.readString(source.resolve("public/app.js"))).contains("before");
    }
    @Test void staleReviewFailedChecksAndChangedRuntimeCannotStart()throws Exception {
        var d=service.preview(execution.id);assertThatThrownBy(()->service.start(d.id,"stale")).hasMessageContaining("Review");
        execution.status="FAILED";attempts.save(execution);assertThatThrownBy(()->service.start(d.id,d.reviewHash)).hasMessageContaining("passing");
        execution.status="PASSED";attempts.save(execution);when(runtime.status(anyBoolean())).thenReturn(new DockerSandbox.Status(true,true,"NODE24_TEST","sha256:"+"b".repeat(64),"Changed"));
        assertThatThrownBy(()->service.start(d.id,d.reviewHash)).hasMessageContaining("image changed");verify(docker,never()).launch(any(),any(),any());
    }
    @Test void failedLaunchNeverPublishesAUrlAndPendingCleanupBlocksFurtherWork()throws Exception {
        when(docker.launch(any(),any(),any())).thenThrow(new java.io.IOException("fixture"));when(docker.cleanup(any())).thenReturn(false);
        var d=service.preview(execution.id);service.start(d.id,d.reviewHash);eventually(()->store.get(d.id).status.equals("CLEANUP_PENDING")&&store.get(d.id).cleanupPending);
        assertThat(store.get(d.id).url).isBlank();var next=service.preview(execution.id);assertThatThrownBy(()->service.start(next.id,next.reviewHash)).hasMessageContaining("cleanup");
        when(docker.cleanup(any())).thenReturn(true);assertThat(service.cleanup(d.id).cleanupPending).isFalse();
    }
    @Test void cancellationRejectsLateSuccessAndRetainsOwnedCleanup()throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(docker.launch(any(),any(),any())).thenAnswer(call->{entered.countDown();release.await(8,TimeUnit.SECONDS);if(((BooleanSupplier)call.getArgument(2)).getAsBoolean())throw new CancellationException();return 49123;});
        var d=service.preview(execution.id);service.start(d.id,d.reviewHash);assertThat(entered.await(8,TimeUnit.SECONDS)).isTrue();assertThat(service.stop(d.id).status).isEqualTo("CANCELLED");release.countDown();
        eventually(()->!store.get(d.id).cleanupPending);assertThat(store.get(d.id).status).isEqualTo("CANCELLED");assertThat(store.get(d.id).url).isBlank();
    }
    @Test void expiryRestartAndFailedHealthRetainTruthfulState()throws Exception {
        var d=live();service.recover();assertThat(store.get(d.id).status).isEqualTo("LIVE");
        store.update(d.id,value->value.expiresAt=Instant.now().minusSeconds(1),null);assertThat(service.refresh(d.id).status).isEqualTo("EXPIRED");
        var next=live();when(docker.verifyLive(any())).thenReturn(false);assertThat(service.refresh(next.id).status).isEqualTo("FAILED");assertThat(store.get(next.id).url).isBlank();
        store.update(next.id,value->{value.status="DEPLOYING";value.cleanupPending=true;},null);service.recover();assertThat(store.get(next.id).status).isEqualTo("INTERRUPTED");assertThat(store.get(next.id).cleanupPending).isFalse();
    }
    @Test void repeatedPreviewsReuseReviewButFinishedAttemptsAreLimited()throws Exception {
        var first=service.preview(execution.id);assertThat(service.preview(execution.id).id).isEqualTo(first.id);service.stop(first.id);
        for(int i=0;i<2;i++){var d=service.preview(execution.id);service.stop(d.id);}
        assertThatThrownBy(()->service.preview(execution.id)).hasMessageContaining("three-deployment");verify(docker,never()).launch(any(),any(),any());
    }
}
