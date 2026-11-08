package dev.workspace;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

/** Optional installed-binary compatibility check; no real model endpoint or account data is used. */
@EnabledIfEnvironmentVariable(named="WORKSPACE_CODEX_TEST_EXECUTABLE",matches=".+")
class CodexCompatibilityTests {
    @TempDir Path cwd;
    @Test void installedAppServerHasNoPlanningToolsAndReturnsStructuredOutput() throws Exception {
        JsonMapper json=JsonMapper.builder().build();
        AtomicReference<JsonNode> captured=new AtomicReference<>();
        AtomicReference<String> authorization=new AtomicReference<>();
        HttpServer provider=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String answer="{\"goal\":\"Synthetic plan\",\"changes\":[],\"verification\":[],\"openQuestions\":[]}";
        Map<String,Object> message=Map.of("type","message","role","assistant","id","probe-message","status","completed",
            "content",List.of(Map.of("type","output_text","text",answer,"annotations",List.of())));
        provider.createContext("/v1/responses",exchange -> {
            try {
                captured.set(json.readTree(exchange.getRequestBody().readAllBytes()));
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                List<Map<String,Object>> events=List.of(
                    Map.of("type","response.created","response",Map.of("id","probe","status","in_progress","output",List.of())),
                    Map.of("type","response.output_item.added","output_index",0,"item",Map.of("type","message","role","assistant","id","probe-message","content",List.of())),
                    Map.of("type","response.output_text.delta","item_id","probe-message","output_index",0,"content_index",0,"delta",answer),
                    Map.of("type","response.output_item.done","output_index",0,"item",message),
                    Map.of("type","response.completed","response",Map.of("id","probe","status","completed","output",List.of(message),
                        "usage",Map.of("input_tokens",1,"output_tokens",1,"total_tokens",2)))
                );
                StringBuilder body=new StringBuilder();
                for(var event:events) body.append("data: ").append(json.writeValueAsString(event)).append("\n\n");
                byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","text/event-stream");
                exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        provider.start();
        try {
            List<String> command=new ArrayList<>(CodexConnection.command(System.getenv("WORKSPACE_CODEX_TEST_EXECUTABLE")));
            command.addAll(List.of("-c","model_provider=\"workspace_fixture\"",
                "-c","model_providers.workspace_fixture.name=\"LocalCompatibilityTest\"",
                "-c","model_providers.workspace_fixture.base_url=\"http://127.0.0.1:"+provider.getAddress().getPort()+"/v1\"",
                "-c","model_providers.workspace_fixture.wire_api=\"responses\"",
                "-c","model_providers.workspace_fixture.requires_openai_auth=false",
                "-c","model_providers.workspace_fixture.supports_websockets=false"));
            ProcessBuilder builder=new ProcessBuilder(command).directory(cwd.toFile());
            Path diagnostics=cwd.resolve("server.stderr");
            builder.redirectError(diagnostics.toFile());
            builder.environment().remove("CODEX_THREAD_ID");builder.environment().remove("CODEX_TURN_ID");
            try(var client=new CodexConnection(builder.start())) {
                client.initialize();
                String result=client.plan("Synthetic compatibility test. Return an empty structured plan without tools.",cwd,Duration.ofSeconds(30));
                assertThat(json.readTree(result).path("goal").asText()).isEqualTo("Synthetic plan");
            } catch(Exception e) {
                String diagnostic=Files.readString(diagnostics);
                throw new AssertionError("Installed-binary compatibility check failed: " + diagnostic.substring(0,Math.min(2000,diagnostic.length())),e);
            }
            assertThat(captured.get()).isNotNull();
            JsonNode tools=captured.get().path("tools");
            assertThat(tools.isMissingNode() || (tools.isArray() && tools.isEmpty()))
                .as("The model must receive no tool catalog (omitted or empty)").isTrue();
            assertThat(authorization.get() == null).as("No credential may be sent to the synthetic provider").isTrue();
        } finally { provider.stop(0); }
    }
}
