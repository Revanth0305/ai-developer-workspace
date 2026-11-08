package dev.workspace;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PlanContextTests {
    @TempDir Path root;
    Project project;
    Task task;
    WorkspaceFiles files;
    PlanContext context;
    @BeforeEach void setup() throws Exception {
        Path platform = Files.createDirectory(root.resolve("platform"));
        Path source = Files.createDirectory(root.resolve("project"));
        project = new Project("Sample", source.toString(), "Use the existing stack");
        task = new Task(project.id, "Plan a validation feature");
        files = new WorkspaceFiles(platform.toString()); context = new PlanContext(files);
    }
    Path source(String file) { return Path.of(project.directory).resolve(file); }
    @Test void includesOnlySelectedContentAndRedactsInstructionSettingsAndSourceSecrets() throws Exception {
        Files.writeString(source("selected.ts"), "const api_key = 'fixture-secret-value';\nconst safe = 42;");
        Files.writeString(source("unselected.ts"), "UNSELECTED_CONTENT_MUST_STAY_LOCAL");
        task.instruction += "\nAuthorization: Bearer fixture-token-123456789";
        project.instructions += "\npassword=fixture-password";
        var preview = context.preview(project, task, List.of("selected.ts"));
        assertThat(preview.prompt()).contains("const safe = 42", "unselected.ts", "[REDACTED]")
            .doesNotContain("fixture-secret-value", "fixture-password", "fixture-token", "UNSELECTED_CONTENT_MUST_STAY_LOCAL");
        assertThat(preview.selectedFiles()).containsExactly("selected.ts");
        assertThat(preview.warnings()).anyMatch(w -> w.contains("redacted"));
    }
    @Test void rawContentSettingsAndListingChangesInvalidateTheReviewedHash() throws Exception {
        Files.writeString(source("config.ts"), "password=first-fixture-value");
        var first = context.preview(project, task, List.of("config.ts"));
        Files.writeString(source("config.ts"), "password=second-fixture-value");
        var second = context.preview(project, task, List.of("config.ts"));
        assertThat(first.prompt()).isEqualTo(second.prompt());
        assertThat(first.hash()).isNotEqualTo(second.hash());
        project.instructions = "Different conventions";
        assertThat(context.preview(project, task, List.of("config.ts")).hash()).isNotEqualTo(second.hash());
        Files.writeString(source("new.ts"), "new source");
        assertThat(context.preview(project, task, List.of("config.ts")).hash()).isNotEqualTo(second.hash());
    }
    @Test void contextOrderIsStableAndUnselectedContentDoesNotAffectTheHash() throws Exception {
        Files.writeString(source("a.ts"), "alpha"); Files.writeString(source("b.ts"), "beta");
        var first = context.preview(project, task, List.of("b.ts", "a.ts", "a.ts"));
        assertThat(context.preview(project, task, List.of("a.ts", "b.ts")).hash()).isEqualTo(first.hash());
        assertThat(first.selectedFiles()).containsExactly("a.ts", "b.ts");
    }
    @Test void rejectsOversizedContextExcludedFilesAndTraversal() throws Exception {
        Files.writeString(source("large.ts"), "x".repeat(12001));
        Files.writeString(source("a.ts"), "x".repeat(12000));
        Files.writeString(source("b.ts"), "x".repeat(12000));
        Files.writeString(source("c.ts"), "x");
        for (List<String> selection : List.of(List.of("large.ts"), List.of("a.ts","b.ts","c.ts"), List.of("../outside.ts"), List.of(".env")))
            assertThatThrownBy(() -> context.preview(project, task, selection)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> context.preview(project, task, java.util.stream.IntStream.range(0,9).mapToObj(i -> i+".ts").toList())).hasMessageContaining("eight");
    }
    @Test void redactsPrivateKeysAndCommonHostingTokens() {
        String source = "-----BEGIN PRIVATE KEY-----\nSYNTHETIC_BASE64\n-----END PRIVATE KEY-----\n" + "glpat-abcdefghijklmnopqrstuv";
        assertThat(PlanContext.redact(source)).contains("[REDACTED PRIVATE KEY]", "[REDACTED TOKEN]").doesNotContain("SYNTHETIC_BASE64", "abcdefghijklmnop");
    }
    @Test void structuredPlansValidatePathsAndRemainProposals() {
        PlanDocument document = new PlanDocument(files);
        String response = "{\"goal\":\"Add validation\",\"changes\":[{\"path\":\"src/form.ts\",\"change\":\"Validate required values\"}],\"verification\":[\"Run relevant tests\"],\"openQuestions\":[]}";
        assertThat(document.validateAndRender(project,response)).contains("src/form.ts", "No files were edited");
        for (String path : List.of("../outside.ts", "C:/outside.ts", ".env", "node_modules/generated.ts"))
            assertThatThrownBy(() -> document.validateAndRender(project,response.replace("src/form.ts",path))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> document.validateAndRender(project,"{\"goal\":\"bad\"}")).isInstanceOf(RuntimeException.class);
        assertThat(Files.exists(source("src/form.ts"))).isFalse();
    }
}
