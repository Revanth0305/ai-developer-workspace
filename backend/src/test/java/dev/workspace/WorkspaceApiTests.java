package dev.workspace;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "workspace.codex.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:workspace-api-tests;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class WorkspaceApiTests {
    static final Path fixture;
    static {
        try { fixture = Files.createTempDirectory("workspace-api-tests-"); Files.createDirectory(fixture.resolve("platform")); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("workspace.platform-root", () -> fixture.resolve("platform").toString());
    }
    @LocalServerPort int port;
    final HttpClient client = HttpClient.newHttpClient();
    final JsonMapper json = JsonMapper.builder().build();

    HttpResponse<String> request(String method, String path, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path));
        builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    JsonNode body(HttpResponse<String> response) { return json.readTree(response.body()); }
    String register(Path folder) throws Exception {
        var response = request("POST", "/projects", Map.of("name", "Fixture project", "directory", folder.toString(), "instructions", "Use tests"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return body(response).get("id").asText();
    }

    @Test void projectTaskSettingsAndCancellationAreStoredAndIsolated() throws Exception {
        Path first = Files.createTempDirectory(fixture, "first-");
        Path second = Files.createTempDirectory(fixture, "second-");
        String firstId = register(first); String secondId = register(second);
        var created = request("POST", "/tasks", Map.of("projectId", firstId, "instruction", "Add form validation"));
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(body(created).get("status").asText()).isEqualTo("DRAFT");
        String taskId = body(created).get("id").asText();
        assertThat(body(request("GET", "/tasks?projectId=" + firstId, null)).size()).isEqualTo(1);
        assertThat(body(request("GET", "/tasks?projectId=" + secondId, null)).size()).isZero();
        var settings = request("PUT", "/projects/" + firstId, Map.of("name", "Updated project", "instructions", "Never change deployment settings"));
        assertThat(settings.statusCode()).isEqualTo(200);
        assertThat(body(request("GET", "/projects/" + firstId, null)).get("instructions").asText()).isEqualTo("Never change deployment settings");
        assertThat(body(request("GET", "/projects/" + secondId, null)).get("instructions").asText()).isEqualTo("Use tests");
        assertThat(body(request("POST", "/tasks/" + taskId + "/cancel", null)).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(request("POST", "/tasks/" + taskId + "/cancel", null).statusCode()).isEqualTo(409);
        assertThat(body(request("GET", "/activity?projectId=" + firstId, null)).size()).isEqualTo(4);
    }

    @Test void sourceInspectionRejectsTraversalHiddenAndLargeFiles() throws Exception {
        Path folder = Files.createTempDirectory(fixture, "source-");
        Files.writeString(folder.resolve("README.md"), "# Test project");
        Files.writeString(folder.resolve(".env"), "NOT_A_REAL_SECRET=fixture");
        Files.writeString(folder.resolve("credentials.json"), "{}");
        Files.createDirectories(folder.resolve("node_modules"));
        Files.writeString(folder.resolve("node_modules/generated.js"), "generated");
        Files.writeString(folder.resolve("large.txt"), "x".repeat(65537));
        String id = register(folder);
        String files = request("GET", "/projects/" + id + "/files", null).body();
        assertThat(files).contains("README.md").doesNotContain(".env", "credentials", "generated");
        assertThat(request("GET", "/projects/" + id + "/file?path=README.md", null).body()).contains("# Test project");
        for (String path : new String[]{"../outside.txt", ".env", "credentials.json", "node_modules/generated.js", "large.txt"}) {
            assertThat(request("GET", "/projects/" + id + "/file?path=" + path, null).statusCode()).as(path).isEqualTo(400);
        }
    }

    @Test void registrationRejectsPlatformMissingAndOverlappingDirectories() throws Exception {
        for (Path path : new Path[]{fixture.resolve("platform"), fixture, fixture.resolve("missing")}) {
            assertThat(request("POST", "/projects", Map.of("name", "Invalid", "directory", path.toString())).statusCode()).isEqualTo(400);
        }
        Path folder = Files.createTempDirectory(fixture, "overlap-"); register(folder);
        Path child = Files.createDirectory(folder.resolve("child"));
        assertThat(request("POST", "/projects", Map.of("name", "Child", "directory", child.toString())).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/projects", Map.of("name", "Duplicate", "directory", folder.toString())).statusCode()).isEqualTo(400);
    }

    @Test void invalidTasksAndForeignBrowserOriginsAreRejected() throws Exception {
        assertThat(request("POST", "/tasks", Map.of("projectId", "not-a-uuid", "instruction", "Test")).statusCode()).isEqualTo(400);
        assertThat(request("POST", "/tasks", Map.of("projectId", java.util.UUID.randomUUID(), "instruction", "Test")).statusCode()).isEqualTo(404);
        var foreign = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/projects"))
            .header("Origin", "https://untrusted.example").GET().build();
        assertThat(client.send(foreign, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        assertThat(body(request("GET", "/health", null)).get("aiConfigured").asBoolean()).isFalse();
    }
}
