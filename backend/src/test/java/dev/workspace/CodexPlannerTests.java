package dev.workspace;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"workspace.codex.enabled=true", "spring.datasource.url=jdbc:h2:mem:planning-tests;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop"})
class CodexPlannerTests {
    static final Path root;
    static {
        try { root=Files.createTempDirectory("planner-tests-"); Files.createDirectory(root.resolve("platform")); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("workspace.platform-root", () -> root.resolve("platform").toString()); }
    @Autowired CodexPlanner planner;
    @Autowired PlanContext context;
    @Autowired ProjectRepository projects;
    @Autowired TaskRepository tasks;
    @Autowired PlanAttemptRepository attempts;
    @Autowired ActivityRepository activity;
    @MockitoBean PlanningClientFactory clients;
    Project project;
    PlanningClient worker;
    static final String PLAN="{\"goal\":\"Review source\",\"changes\":[{\"path\":\"README.md\",\"change\":\"Describe the feature\"}],\"verification\":[\"Review documentation\"],\"openQuestions\":[]}";
    @BeforeEach void setup() throws Exception {
        activity.deleteAll(); attempts.deleteAll(); tasks.deleteAll(); projects.deleteAll();
        Path folder=Files.createTempDirectory(root,"source-"); Files.writeString(folder.resolve("README.md"),"# Sample source");
        project=projects.save(new Project("Sample",folder.toString(),"Keep planning read-only"));
        PlanningClient probe=mock(PlanningClient.class);
        when(probe.request(eq("account/read"),any())).thenReturn(JsonMapper.builder().build().readTree("{\"account\":{\"type\":\"chatgpt\"}}"));
        when(clients.create(anyString(),any())).thenReturn(probe);
        assertThat(planner.refreshStatus().authenticated()).isTrue();
        worker=mock(PlanningClient.class);
        when(worker.plan(anyString(),any(),any())).thenReturn(PLAN);
        when(worker.threadId()).thenReturn("synthetic-thread");
        when(clients.create(anyString(),any())).thenReturn(worker);
        clearInvocations(clients);
    }
    Task draft() { return tasks.save(new Task(project.id,"Plan a documentation feature")); }
    String hash(Task task) { return context.preview(project,task,List.of("README.md")).hash(); }
    PlanAttempt start(Task task) { return planner.start(task.id,List.of("README.md"),hash(task)); }
    void eventually(BooleanSupplier condition) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean()) { if(System.nanoTime()>until) fail("Timed out waiting for planner state"); Thread.sleep(20); }
    }
    PlanAttempt startWhenIdle(Task task) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while (true) {
            try { return start(task); }
            catch(org.springframework.web.server.ResponseStatusException e) {
                if(e.getStatusCode().value()!=409 || System.nanoTime()>until) throw e;
                Thread.sleep(20);
            }
        }
    }
    @Test void successfulPlansPersistSeparatelyAndNeverChangeSource() throws Exception {
        Task task=draft(); PlanAttempt attempt=start(task);
        eventually(() -> tasks.findById(task.id).orElseThrow().status.equals("PLANNED"));
        assertThat(attempts.findById(attempt.id).orElseThrow().result).contains("README.md","No files were edited");
        assertThat(Files.readString(Path.of(project.directory).resolve("README.md"))).isEqualTo("# Sample source");
        verify(worker,timeout(3000)).close();
    }
    @Test void connectionCreationFailureReleasesSlotAndAllowsAnotherTask() throws Exception {
        when(clients.create(anyString(),any())).thenThrow(new java.io.IOException("fixture failure")).thenReturn(worker);
        Task first=draft(); PlanAttempt attempt=start(first);
        eventually(() -> attempts.findById(attempt.id).orElseThrow().status.equals("FAILED"));
        Task next=draft(); startWhenIdle(next);
        eventually(() -> tasks.findById(next.id).orElseThrow().status.equals("PLANNED"));
        verify(worker,timeout(3000)).close();
    }
    @Test void cancellationBeforeConnectionPreventsModelRequestAndKeepsSlotUntilCleanup() throws Exception {
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        when(clients.create(anyString(),any())).thenAnswer(inv -> { entered.countDown(); assertThat(release.await(5,TimeUnit.SECONDS)).isTrue(); return worker; });
        Task first=draft(); PlanAttempt attempt=start(first); assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
        planner.cancel(first.id);
        Task next=draft(); assertThatThrownBy(() -> start(next)).hasMessageContaining("running or stopping");
        release.countDown(); verify(worker,timeout(3000)).close();
        verify(worker,never()).initialize(); verify(worker,never()).plan(anyString(),any(),any());
        assertThat(attempts.findById(attempt.id).orElseThrow().status).isEqualTo("CANCELLED");
        startWhenIdle(next); eventually(() -> tasks.findById(next.id).orElseThrow().status.equals("PLANNED"));
    }
    @Test void cancellationDuringPlanningRejectsLateSuccessAndOtherConcurrentRequests() throws Exception {
        CountDownLatch running=new CountDownLatch(1), release=new CountDownLatch(1);
        when(worker.plan(anyString(),any(),any())).thenAnswer(inv -> { running.countDown(); assertThat(release.await(5,TimeUnit.SECONDS)).isTrue(); return PLAN; });
        doAnswer(inv -> { release.countDown(); return null; }).when(worker).interrupt();
        Task first=draft(); PlanAttempt attempt=start(first); assertThat(running.await(5,TimeUnit.SECONDS)).isTrue();
        Task next=draft(); assertThatThrownBy(() -> start(next)).hasMessageContaining("running or stopping");
        assertThat(attempts.findByTaskIdOrderByStartedAtDesc(next.id)).isEmpty();
        planner.cancel(first.id); verify(worker,timeout(3000)).close();
        assertThat(tasks.findById(first.id).orElseThrow().status).isEqualTo("CANCELLED");
        assertThat(attempts.findById(attempt.id).orElseThrow().result).isEmpty();
        startWhenIdle(next); eventually(() -> tasks.findById(next.id).orElseThrow().status.equals("PLANNED"));
    }
    @Test void timeoutOrInvalidOutputFailWithoutLockingTheQueue() throws Exception {
        when(worker.plan(anyString(),any(),any())).thenThrow(new TimeoutException()).thenReturn("{\"invalid\":true}").thenReturn(PLAN);
        Task first=draft(); PlanAttempt timed=start(first);
        eventually(() -> attempts.findById(timed.id).orElseThrow().status.equals("FAILED"));
        assertThat(attempts.findById(timed.id).orElseThrow().error).contains("time limit");
        Task second=draft(); PlanAttempt invalid=startWhenIdle(second);
        eventually(() -> attempts.findById(invalid.id).orElseThrow().status.equals("FAILED"));
        Task third=draft(); startWhenIdle(third);
        eventually(() -> tasks.findById(third.id).orElseThrow().status.equals("PLANNED"));
    }
    @Test void changedContextIsRejectedBeforeAnyAttemptIsStored() throws Exception {
        Task task=draft(); String reviewed=hash(task);
        Files.writeString(Path.of(project.directory).resolve("README.md"),"# Changed source");
        assertThatThrownBy(() -> planner.start(task.id,List.of("README.md"),reviewed)).hasMessageContaining("context changed");
        assertThat(attempts.findByTaskIdOrderByStartedAtDesc(task.id)).isEmpty();
        verify(clients,never()).create(anyString(),any());
    }
    @Test void restartRecoveryRetainsCompletedAttemptsAndRecoversOrphanTasks() {
        Task task=draft(); task.status="PLANNING"; tasks.save(task);
        PlanAttempt interrupted=attempts.save(new PlanAttempt(task.id,"Reviewed context"));
        Task orphan=draft(); orphan.status="PLANNING"; tasks.save(orphan);
        Task complete=draft(); complete.status="PLANNED"; tasks.save(complete);
        PlanAttempt saved=new PlanAttempt(complete.id,"Earlier context"); saved.status="SUCCEEDED"; saved.result="Earlier plan"; attempts.save(saved);
        planner.recover();
        assertThat(attempts.findById(interrupted.id).orElseThrow().status).isEqualTo("INTERRUPTED");
        assertThat(tasks.findById(task.id).orElseThrow().status).isEqualTo("FAILED");
        assertThat(tasks.findById(orphan.id).orElseThrow().status).isEqualTo("FAILED");
        assertThat(attempts.findById(saved.id).orElseThrow().result).isEqualTo("Earlier plan");
    }
    @Test void incompatibleConnectionStaysUnavailableAndCannotCreateAnAttempt() throws Exception {
        PlanningClient incompatible=mock(PlanningClient.class);
        when(incompatible.request(eq("account/read"),any())).thenReturn(JsonMapper.builder().build().readTree("{\"account\":{\"type\":\"chatgpt\"}}"));
        doThrow(new java.io.IOException("restrictions not applied")).when(incompatible).prepare(any());
        when(clients.create(anyString(),any())).thenReturn(incompatible);
        assertThat(planner.refreshStatus().authenticated()).isFalse();
        Task task=draft();
        assertThatThrownBy(() -> start(task)).hasMessageContaining("unavailable");
        assertThat(attempts.findByTaskIdOrderByStartedAtDesc(task.id)).isEmpty();
    }
}
