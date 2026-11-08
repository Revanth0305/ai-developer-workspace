package dev.workspace;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class CodexConnectionTests {
    @TempDir Path cwd;
    @Test void verifiesConfigurationUsesStructuredOutputAndCollectsTheCompletedTurn() throws Exception {
        try (var process = new Server("success"); var client = new CodexConnection(process)) {
            client.initialize();
            assertThat(client.plan("Reviewed synthetic context",cwd,Duration.ofSeconds(3))).isEqualTo("{\"goal\":\"Synthetic\"}");
            JsonNode start=process.requests.stream().filter(n -> n.path("method").asText().equals("thread/start")).findFirst().orElseThrow().path("params");
            assertThat(start.path("config").path("mcp_servers").path("test.server").path("enabled").asBoolean(true)).isFalse();
            assertThat(start.path("config").path("mcp_servers").path("test.server").has("tool_timeout_sec")).isFalse();
            assertThat(start.path("config").path("mcp_servers").path("test.server").path("command").asText()).isEqualTo("never-run");
            assertThat(start.path("dynamicTools").isEmpty()).isTrue();
            JsonNode turn=process.requests.stream().filter(n -> n.path("method").asText().equals("turn/start")).findFirst().orElseThrow().path("params");
            assertThat(turn.path("outputSchema").path("required").size()).isEqualTo(4);
            assertThat(turn.path("sandboxPolicy").path("networkAccess").asBoolean(true)).isFalse();
        }
    }
    @Test void refusesUnrestrictedFeaturesAndInstructionSourcesBeforeSendingAnyPrompt() throws Exception {
        for(String mode:List.of("feature-enabled","instruction-leak","network-enabled")) {
            try(var process=new Server(mode);var client=new CodexConnection(process)) {
                client.initialize();
                assertThatThrownBy(() -> client.plan("Never send",cwd,Duration.ofSeconds(2))).isInstanceOf(IOException.class);
                assertThat(process.requests).noneMatch(n -> n.path("method").asText().equals("turn/start"));
            }
        }
    }
    @Test void timeoutIsBoundedAndClosingUnblocksPendingRequests() throws Exception {
        try(var process=new Server("no-completion");var client=new CodexConnection(process)) {
            client.initialize();
            assertThatThrownBy(() -> client.plan("Synthetic",cwd,Duration.ofMillis(150))).isInstanceOf(TimeoutException.class);
        }
        try(var process=new Server("hang");var client=new CodexConnection(process)) {
            CompletableFuture<Void> call=CompletableFuture.runAsync(() -> { try { client.initialize(); } catch(Exception e) { throw new CompletionException(e); } });
            assertThat(process.received.await(2,TimeUnit.SECONDS)).isTrue();
            client.close();
            assertThatThrownBy(() -> call.get(2,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
        }
    }
    @Test void rejectsExecutionApprovalsToolActivityWrongTurnsAndOversizedOutput() throws Exception {
        for(String mode:List.of("approval","tool-activity","wrong-turn","oversized","aggregate-output")) {
            try(var process=new Server(mode);var client=new CodexConnection(process)) {
                client.initialize();
                assertThatThrownBy(() -> client.plan("Synthetic",cwd,Duration.ofSeconds(3))).isInstanceOf(Exception.class);
                if(mode.equals("approval")) assertThat(process.sent.toString(StandardCharsets.UTF_8)).contains("decline");
            }
        }
    }
    @Test void protocolErrorsDoNotExposeProviderDetails() throws Exception {
        try(var process=new Server("rpc-error");var client=new CodexConnection(process)) {
            assertThatThrownBy(client::initialize).hasStackTraceContaining("Codex rejected request (-123)").hasStackTraceContaining("ExecutionException");
            try { client.initialize(); } catch(Exception e) { assertThat(e.toString()).doesNotContain("PROVIDER_SECRET_DETAIL"); }
        }
    }

    static final class Server extends Process implements AutoCloseable {
        final JsonMapper json=JsonMapper.builder().build();
        final PipedInputStream clientOutput=new PipedInputStream(2*1024*1024);
        final PipedOutputStream serverOutput;
        final PipedInputStream serverInput=new PipedInputStream(2*1024*1024);
        final PipedOutputStream clientInput;
        final ByteArrayOutputStream sent=new ByteArrayOutputStream();
        final CountDownLatch received=new CountDownLatch(1), exited=new CountDownLatch(1);
        final List<JsonNode> requests=new CopyOnWriteArrayList<>();
        volatile boolean alive=true;
        final String mode;
        Server(String mode) throws IOException {
            this.mode=mode; serverOutput=new PipedOutputStream(clientOutput); clientInput=new PipedOutputStream(serverInput);
            Thread.ofVirtual().start(() -> {
                try(var reader=new BufferedReader(new InputStreamReader(serverInput,StandardCharsets.UTF_8))) {
                    String line;
                    while((line=reader.readLine())!=null) {
                        JsonNode request=json.readTree(line); requests.add(request); received.countDown();
                        if(!request.has("id") || !request.has("method")) continue;
                        respond(request);
                    }
                } catch(Exception ignored) { }
            });
        }
        void respond(JsonNode req) throws Exception {
            String method=req.path("method").asText();
            if(mode.equals("hang")) return;
            if(mode.equals("rpc-error")) { write(Map.of("id",req.get("id"),"error",Map.of("code",-123,"message","PROVIDER_SECRET_DETAIL"))); return; }
            Object result=Map.of();
            if(method.equals("config/read")) {
                Map<String,Object> features=new LinkedHashMap<>(); CodexConnection.DISABLED_FEATURES.forEach(f -> features.put(f,false));
                if(mode.equals("feature-enabled")) features.put("shell_tool",true);
                Map<String,Object> transport=new LinkedHashMap<>(); transport.put("command","never-run"); transport.put("tool_timeout_sec",null);
                result=Map.of("config",Map.of("features",features,"mcp_servers",Map.of("test.server",transport),"apps",Map.of("_default",Map.of("enabled",true)),"plugins",Map.of()));
            }
            if(method.equals("thread/start")) result=Map.of("thread",Map.of("id","thread-1"),"sandbox",Map.of("type","readOnly","networkAccess",mode.equals("network-enabled")),"approvalPolicy","never","instructionSources",mode.equals("instruction-leak")?List.of("unexpected.md"):List.of());
            if(method.equals("turn/start")) result=Map.of("turn",Map.of("id","turn-1"));
            write(Map.of("id",req.get("id"),"result",result));
            if(!method.equals("turn/start") || mode.equals("no-completion")) return;
            if(mode.equals("approval")) { write(Map.of("id",900,"method","item/commandExecution/requestApproval","params",Map.of())); return; }
            if(mode.equals("oversized")) { serverOutput.write("x".repeat(1024*1024+1).getBytes(StandardCharsets.UTF_8)); serverOutput.flush(); return; }
            if(mode.equals("tool-activity")) { event("item/started",Map.of("item",Map.of("type","commandExecution"))); return; }
            if(mode.equals("aggregate-output")) {
                for(int i=0;i<2;i++) event("item/completed",Map.of("item",Map.of("type","agentMessage","id","message-"+i,"text","x".repeat(40000))));
                return;
            }
            event("item/completed",Map.of("item",Map.of("type","agentMessage","phase","final_answer","id","answer","text","{\"goal\":\"Synthetic\"}")));
            event("turn/completed",Map.of("turn",Map.of("id",mode.equals("wrong-turn")?"wrong":"turn-1","status","completed")));
        }
        void event(String name,Map<String,Object> params) throws IOException {
            Map<String,Object> values=new LinkedHashMap<>(params);values.put("threadId","thread-1");write(Map.of("method",name,"params",values));
        }
        synchronized void write(Object value) throws IOException { serverOutput.write((json.writeValueAsString(value)+"\n").getBytes(StandardCharsets.UTF_8));serverOutput.flush(); }
        @Override public OutputStream getOutputStream() { return new FilterOutputStream(clientInput) {
            @Override public void write(byte[] b,int off,int len) throws IOException { sent.write(b,off,len);out.write(b,off,len); }
            @Override public void write(int b) throws IOException { sent.write(b);out.write(b); }
        }; }
        @Override public InputStream getInputStream() { return clientOutput; }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() throws InterruptedException { exited.await();return 0; }
        @Override public boolean waitFor(long timeout,TimeUnit unit) throws InterruptedException { return exited.await(timeout,unit); }
        @Override public int exitValue() { if(alive) throw new IllegalThreadStateException();return 0; }
        @Override public boolean isAlive() { return alive; }
        @Override public Stream<ProcessHandle> descendants() { return Stream.empty(); }
        @Override public void destroy() {
            alive=false;exited.countDown();
            try { serverOutput.close();clientInput.close();serverInput.close();clientOutput.close(); } catch(IOException ignored) { }
        }
        @Override public Process destroyForcibly() { destroy();return this; }
        @Override public void close() { destroy(); }
    }
}
