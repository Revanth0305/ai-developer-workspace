package dev.workspace;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:publishing-tests;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","workspace.git.enabled=true"})
class GitPublishingTests {
    static final Path root;
    static {try {root=Files.createTempDirectory("publishing-tests-");Files.createDirectory(root.resolve("platform"));}catch(Exception e){throw new RuntimeException(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("workspace.platform-root",()->root.resolve("platform").toString());}
    @Autowired GitPublishing service;
    @Autowired GitPublicationRepository publications;
    @Autowired ExecutionAttemptRepository attempts;
    @Autowired ProjectRepository projects;
    @Autowired TaskRepository tasks;
    @Autowired CandidateWorkspace workspaces;
    @MockitoBean GitHubTransport github;
    ExecutionAttempt attempt;Path source,candidate;
    GitPublishing.Input input=new GitPublishing.Input("ExampleUser","verification","Fixture","fixture@localhost","Apply reviewed greeting change");
    @BeforeEach void setup() throws Exception {
        publications.deleteAll();attempts.deleteAll();tasks.deleteAll();projects.deleteAll();
        source=Files.createTempDirectory(root,"source-");
        Files.writeString(source.resolve("greeting.mjs"),"export const greeting = 'before';\n");
        Files.write(source.resolve("untouched.png"),new byte[]{0,1,2,3});
        workspaces.git(source,"init","--quiet");workspaces.git(source,"add","--all");
        workspaces.git(source,"-c","user.name=Fixture","-c","user.email=fixture@localhost","-c","commit.gpgSign=false","commit","--quiet","-m","Source baseline");
        Project project=projects.save(new Project("Fixture",source.toString(),""));Task task=tasks.save(new Task(project.id,"Change greeting"));
        var snapshot=workspaces.inspect(project);attempt=new ExecutionAttempt(task.id,UUID.randomUUID());
        attempt.status="PASSED";attempt.exitCode=0;attempt.testCount=1;attempt.sourceFingerprint=snapshot.fingerprint();attempt.baseCommit=snapshot.commit();
        candidate=root.resolve("platform/.runtime/executions").resolve(attempt.id.toString()).resolve("candidate");workspaces.create(candidate,snapshot);
        String content="export const greeting = 'after';\n";
        workspaces.apply(candidate,List.of(new ChangeDocument.Change("greeting.mjs","write",content)));
        attempt.diff=workspaces.diff(candidate);attempt.candidateFingerprint=workspaces.candidateHash(candidate);
        attempt.selectedPaths="{\"files\":[\"greeting.mjs\"],\"newFiles\":[]}";
        attempt.proposal="{\"summary\":\"Greeting update\",\"changes\":[{\"path\":\"greeting.mjs\",\"operation\":\"write\",\"content\":\"export const greeting = 'after';\\n\"}]}";
        attempts.save(attempt);
    }
    Path privateRoot(){return root.resolve("platform/.runtime/publications").resolve(attempt.id.toString());}
    GitPublication committed() throws Exception {var preview=service.prepare(attempt.id,input);return service.commit(attempt.id,preview.reviewHash);}
    @Test void commitRetainsRealParentBinaryFilesAndSourceWithoutSharingObjectStorage() throws Exception {
        var p=committed();assertThat(p.status).isEqualTo("COMMITTED");
        assertThat(workspaces.git(privateRoot(),"rev-parse",p.commitId+"^").strip()).isEqualTo(attempt.baseCommit);
        assertThat(workspaces.git(privateRoot(),"diff","--name-only",p.baseCommit,p.commitId).strip()).isEqualTo("greeting.mjs");
        assertThat(workspaces.git(privateRoot(),"rev-parse",p.commitId+":untouched.png")).isEqualTo(workspaces.git(source,"rev-parse","HEAD:untouched.png"));
        assertThat(workspaces.git(privateRoot(),"show",p.commitId+":greeting.mjs")).contains("'after'");
        assertThat(Files.exists(privateRoot().resolve(".git/objects/info/alternates"))).isFalse();
        assertThat(workspaces.git(source,"rev-parse","HEAD").strip()).isEqualTo(attempt.baseCommit);
        assertThat(workspaces.git(source,"status","--porcelain")).isBlank();
        assertThat(workspaces.git(source,"branch","--list","codex/*")).isBlank();
        verifyNoInteractions(github);
    }
    @Test void staleReviewDirtySourceAndChangedCandidateCannotCommit() throws Exception {
        var preview=service.prepare(attempt.id,input);
        assertThatThrownBy(()->service.commit(attempt.id,"0".repeat(64))).hasMessageContaining("Review");
        Files.writeString(source.resolve("unfinished.txt"),"Keep this unfinished work");
        assertThatThrownBy(()->service.commit(attempt.id,preview.reviewHash)).hasMessageContaining("Commit or stash");
        assertThat(Files.readString(source.resolve("unfinished.txt"))).contains("unfinished");
        Files.delete(source.resolve("unfinished.txt"));Files.writeString(candidate.resolve("greeting.mjs"),"tampered");
        assertThatThrownBy(()->service.commit(attempt.id,preview.reviewHash)).hasMessageContaining("changed");
        verifyNoInteractions(github);
    }
    @Test void failedChecksCleanupAndUnauthorizedProposalCannotPrepare() throws Exception {
        attempt.status="FAILED";attempts.save(attempt);
        assertThatThrownBy(()->service.prepare(attempt.id,input)).hasMessageContaining("passing");
        attempt.status="PASSED";attempt.cleanupPending=true;attempts.save(attempt);
        assertThatThrownBy(()->service.prepare(attempt.id,input)).hasMessageContaining("passing");
        attempt.cleanupPending=false;attempt.selectedPaths="{\"files\":[\"other.mjs\"],\"newFiles\":[]}";attempts.save(attempt);
        assertThatThrownBy(()->service.prepare(attempt.id,input)).hasMessageContaining("unauthorized");
    }
    @Test void pushConfirmsExactBranchAndIsIdempotentWithoutForce() throws Exception {
        var p=committed();when(github.remoteCommit(any(),any())).thenReturn("",p.commitId,p.commitId,p.commitId);
        assertThatThrownBy(()->service.push(p.id,"stale")).hasMessageContaining("Review");
        assertThat(service.push(p.id,p.pushHash).status).isEqualTo("PUSHED");
        assertThat(service.push(p.id,p.pushHash).status).isEqualTo("PUSHED");
        verify(github,times(1)).push(any(),any());
    }
    @Test void conflictingRemoteAndInterruptedPushAreRetainedForExplicitReconciliation() throws Exception {
        var p=committed();when(github.remoteCommit(any(),any())).thenReturn("f".repeat(40));
        assertThatThrownBy(()->service.push(p.id,p.pushHash)).hasMessageContaining("not be overwritten");
        assertThat(service.get(p.id).orElseThrow().status).isEqualTo("PUSH_UNKNOWN");verify(github,never()).push(any(),any());
        var running=service.get(p.id).orElseThrow();running.status="PUSHING";publications.save(running);service.recover();
        assertThat(service.get(p.id).orElseThrow().status).isEqualTo("PUSH_UNKNOWN");
        var retained=service.get(p.id).orElseThrow();when(github.remoteCommit(any(),any())).thenReturn(retained.commitId);
        assertThat(service.push(retained.id,retained.pushHash).status).isEqualTo("PUSHED");verify(github,never()).push(any(),any());
    }
    @Test void destinationAndCommitMetadataRejectUrlsCredentialsAndControlCharacters() {
        assertThatThrownBy(()->GitPublishing.validateInput(new GitPublishing.Input("https://example.com","repo","Name","a@b","Message"))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->GitPublishing.validateInput(new GitPublishing.Input("User","repo.git","Name","a@b","Message"))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->GitPublishing.validateInput(new GitPublishing.Input("User","repo","Name\nInjected","a@b","Message"))).isInstanceOf(RuntimeException.class);
    }
}
