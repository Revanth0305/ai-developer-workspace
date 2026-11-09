package dev.workspace;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.*;

/** A fixed Node profile. Only copied source enters a private volume; no host mounts or secrets. */
@Service
public class DockerSandbox {
    public record Status(boolean enabled,boolean available,String profile,String imageId,String message) {}
    public record CheckResult(int exitCode,String logs,int tests) {}
    private final BoundedProcess processes;
    private final String executable,context,owner;
    private final boolean enabled;
    private final JsonMapper json=JsonMapper.builder().build();
    private Status cached;
    private Instant checked=Instant.EPOCH;
    public DockerSandbox(BoundedProcess processes,@Value("${workspace.execution.enabled:false}") boolean enabled,
            @Value("${workspace.execution.docker-executable:docker}") String executable,
            @Value("${workspace.execution.docker-context:}") String context,
            @Value("${workspace.platform-root}") String root) throws Exception {
        this.processes=processes;this.enabled=enabled;this.executable=executable;
        this.context=context.isBlank()?(System.getProperty("os.name").startsWith("Windows")?"desktop-linux":"default"):context;
        if(!this.context.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("Invalid Docker context name");
        this.owner=CandidateWorkspace.fingerprint(Path.of(root).toRealPath().toString(),Map.of()).substring(0,24);
        cached=new Status(enabled,false,"NODE24_TEST","",enabled?"Check the Docker runner connection.":"Controlled execution is disabled for this session.");
    }
    public synchronized Status status(boolean force) {
        if(!enabled || (!force && checked.plusSeconds(10).isAfter(Instant.now()))) return cached;
        checked=Instant.now();
        try {
            String endpoint=required(call(Duration.ofSeconds(8),()->false,s->{},"context","inspect",context,"--format","{{.Endpoints.docker.Host}}")).strip();
            if(!endpoint.startsWith("npipe://") && !endpoint.startsWith("unix://")) throw new IllegalStateException("Only local Docker endpoints are supported");
            if(!required(call(Duration.ofSeconds(8),()->false,s->{},"info","--format","{{.OSType}}")).strip().equals("linux")) throw new IllegalStateException("Linux containers are required");
            JsonNode image=json.readTree(required(call(Duration.ofSeconds(8),()->false,s->{},"image","inspect","node:24-alpine"))).get(0);
            String id=image.path("Id").asText();
            boolean official=false;
            for(JsonNode digest:image.path("RepoDigests")) if(digest.asText().matches("(?:docker\\.io/library/)?node@sha256:[a-f0-9]{64}")) official=true;
            if(!id.matches("sha256:[a-f0-9]{64}") || !official) throw new IllegalStateException("Prepare the official Node 24 image");
            cached=new Status(true,true,"NODE24_TEST",id,"Node.js 24 tests are available in an isolated Linux container.");
        } catch(Exception e) {
            cached=new Status(true,false,"NODE24_TEST","","Runner unavailable. Start Docker Desktop with Linux containers and prepare the official node:24-alpine image.");
        }
        return cached;
    }
    public boolean enabled() { return enabled; }
    public CheckResult check(UUID attempt,Path staging,String imageId,BooleanSupplier cancelled,Consumer<String> log) throws Exception {
        Status available=status(true);
        if(!available.available() || !available.imageId().equals(imageId)) throw WorkspaceFiles.bad("The runner or image changed. Refresh and review the check run again.");
        String volume=volume(attempt),loader=loader(attempt),runner=runner(attempt);
        required(call(Duration.ofSeconds(10),cancelled,log,"volume","create","--label","jarvis.owner="+owner,"--label","jarvis.attempt="+attempt,volume));
        required(call(Duration.ofSeconds(10),cancelled,log,"create","--name",loader,"--label","jarvis.owner="+owner,"--label","jarvis.attempt="+attempt,
            "--network=none","--mount","type=volume,source="+volume+",target=/workspace,volume-nocopy","--entrypoint","node",imageId,"--version"));
        required(call(Duration.ofSeconds(20),cancelled,log,"cp",staging.toAbsolutePath()+"/.",loader+":/workspace"));
        required(call(Duration.ofSeconds(10),cancelled,log,"rm",loader));
        required(call(Duration.ofSeconds(10),cancelled,log,"create","--name",runner,"--label","jarvis.owner="+owner,"--label","jarvis.attempt="+attempt,
            "--network=none","--read-only","--cap-drop=ALL","--security-opt=no-new-privileges","--memory=256m","--cpus=1","--pids-limit=64",
            "--tmpfs","/tmp:rw,noexec,nosuid,nodev,size=64m","--mount","type=volume,source="+volume+",target=/workspace,readonly,volume-nocopy",
            "--user=65534:65534","--env","HOME=/tmp","--workdir","/workspace","--entrypoint","node",imageId,"--test","--test-reporter=tap"));
        verifyIsolation(runner,volume,attempt);
        if(cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
        var attached=call(Duration.ofSeconds(60),cancelled,log,"start","--attach",runner);
        JsonNode state=json.readTree(required(call(Duration.ofSeconds(8),cancelled,s->{},"inspect","--format","{{json .State}}",runner)));
        if(state.path("Running").asBoolean(true) || !state.path("ExitCode").isNumber()) throw new IllegalStateException("The container exit state was not confirmed");
        int code=state.path("ExitCode").asInt();
        String logs=PlanContext.redact(attached.output());
        if(attached.output().length()>=65536) logs+="\n[Log output truncated at 65,536 characters.]";
        if(state.path("OOMKilled").asBoolean()) logs+="\n[Container exceeded its 256 MB memory limit.]";
        int tests=0;var count=java.util.regex.Pattern.compile("(?m)^# tests (\\d+)\\s*$").matcher(attached.output());
        while(count.find()) tests=Integer.parseInt(count.group(1));
        return new CheckResult(code,logs,tests);
    }
    void verifyIsolation(String runner,String volume,UUID attempt) throws Exception {
        JsonNode config=json.readTree(required(call(Duration.ofSeconds(8),()->false,s->{},"inspect",runner))).get(0);
        JsonNode host=config.path("HostConfig");
        boolean safe=owned(config.path("Config").path("Labels"),attempt)
            && host.path("NetworkMode").asText().equals("none") && host.path("ReadonlyRootfs").asBoolean()
            && !host.path("Privileged").asBoolean(true) && host.path("Memory").asLong()==268435456L
            && host.path("NanoCpus").asLong()==1000000000L && host.path("PidsLimit").asInt()==64
            && config.path("Config").path("User").asText().equals("65534:65534")
            && contains(host.path("CapDrop"),"ALL") && contains(host.path("SecurityOpt"),"no-new-privileges");
        JsonNode mounts=config.path("Mounts");
        int sourceMounts=0;
        if(!mounts.isArray()) safe=false;
        for(JsonNode mount:mounts) {
            if(mount.path("Destination").asText().equals("/tmp") && mount.path("Type").asText().equals("tmpfs")) continue;
            sourceMounts++;
            safe &= mount.path("Type").asText().equals("volume") && mount.path("Name").asText().equals(volume)
                && mount.path("Destination").asText().equals("/workspace") && !mount.path("RW").asBoolean(true);
        }
        safe &= sourceMounts==1 && host.path("Tmpfs").path("/tmp").asText().equals("rw,noexec,nosuid,nodev,size=64m");
        if(!safe) throw new IllegalStateException("The container isolation settings could not be verified");
    }
    private boolean contains(JsonNode values,String value) { for(JsonNode item:values) if(item.asText().equals(value) || item.asText().equals(value+":true")) return true;return false; }
    private boolean owned(JsonNode labels,UUID attempt) { return labels.path("jarvis.owner").asText().equals(owner) && labels.path("jarvis.attempt").asText().equals(attempt.toString()); }
    public boolean cleanup(UUID attempt) {
        boolean clean=true;
        for(String name:List.of(runner(attempt),loader(attempt))) {
            try {
                var inspect=call(Duration.ofSeconds(5),()->false,s->{},"container","inspect",name);
                if(inspect.exitCode()!=0) { if(!missing(inspect.output())) clean=false;continue; }
                JsonNode container=json.readTree(inspect.output()).get(0);
                if(!owned(container.path("Config").path("Labels"),attempt)) { clean=false;continue; }
                if(call(Duration.ofSeconds(8),()->false,s->{},"rm","--force",name).exitCode()!=0) clean=false;
            } catch(Exception e) { clean=false; }
        }
        try {
            var inspect=call(Duration.ofSeconds(5),()->false,s->{},"volume","inspect",volume(attempt));
            if(inspect.exitCode()==0) {
                if(!owned(json.readTree(inspect.output()).get(0).path("Labels"),attempt)
                    || call(Duration.ofSeconds(8),()->false,s->{},"volume","rm",volume(attempt)).exitCode()!=0) clean=false;
            } else if(!missing(inspect.output())) clean=false;
        } catch(Exception e) { clean=false; }
        return clean;
    }
    private boolean missing(String output) { return output.contains("No such container") || output.contains("No such object") || output.contains("no such volume") || output.contains("No such volume"); }
    private String runner(UUID id) { return "jarvis-check-"+id; }
    private String loader(UUID id) { return "jarvis-load-"+id; }
    private String volume(UUID id) { return "jarvis-source-"+id; }
    private String required(BoundedProcess.Result result) { if(result.exitCode()!=0) throw new IllegalStateException("Docker runner command failed");return result.output(); }
    private BoundedProcess.Result call(Duration timeout,BooleanSupplier cancelled,Consumer<String> log,String...args) throws Exception {
        List<String> command=new ArrayList<>(List.of(executable,"--context",context));command.addAll(List.of(args));
        return processes.run(command,null,timeout,cancelled,log);
    }
}
