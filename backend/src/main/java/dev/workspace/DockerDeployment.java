package dev.workspace;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.*;
import java.net.*;
import java.net.http.*;
import java.io.*;

/** Only a trusted static server runs. Project assets enter one private read-only volume. */
@Service
public class DockerDeployment {
    private final BoundedProcess processes;
    private final String executable,context,owner;
    private final JsonMapper json=JsonMapper.builder().build();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER)
        .proxy(new ProxySelector(){public List<Proxy> select(URI uri){return List.of(Proxy.NO_PROXY);}public void connectFailed(URI uri,SocketAddress address,IOException error){}}).build();
    public DockerDeployment(BoundedProcess processes,@Value("${workspace.execution.docker-executable:docker}") String executable,
            @Value("${workspace.execution.docker-context:}") String context,@Value("${workspace.platform-root}") String root) throws Exception {
        this.processes=processes;this.executable=executable;
        this.context=context.isBlank()?(System.getProperty("os.name").startsWith("Windows")?"desktop-linux":"default"):context;
        if(!this.context.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("Invalid Docker context");
        owner=CandidateWorkspace.fingerprint(Path.of(root).toRealPath().toString(),Map.of()).substring(0,24);
    }
    public int launch(Deployment d,Path source,BooleanSupplier cancelled) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(120).toNanos();
        BooleanSupplier stopping=()->cancelled.getAsBoolean() || System.nanoTime()>deadline;
        required(call(20,stopping,"volume","create","--label","jarvis.owner="+owner,"--label","jarvis.deployment="+d.id,volume(d.id)));
        required(call(20,stopping,"create","--name",loader(d.id),"--label","jarvis.owner="+owner,"--label","jarvis.deployment="+d.id,
            "--network=none","--mount","type=volume,source="+volume(d.id)+",target=/site,volume-nocopy","--entrypoint","node",d.imageId,"--version"));
        required(call(20,stopping,"cp",source.toAbsolutePath()+"/.",loader(d.id)+":/site"));
        required(call(10,stopping,"rm",loader(d.id)));
        required(call(20,stopping,"create","--name",server(d.id),"--label","jarvis.owner="+owner,"--label","jarvis.deployment="+d.id,
            "--network=bridge","--publish","127.0.0.1::8080","--restart=no","--read-only","--cap-drop=ALL","--security-opt=no-new-privileges",
            "--memory=128m","--cpus=0.5","--pids-limit=64","--tmpfs","/tmp:rw,noexec,nosuid,nodev,size=32m",
            "--log-driver=json-file","--log-opt=max-size=1m","--log-opt=max-file=1",
            "--mount","type=volume,source="+volume(d.id)+",target=/site,readonly,volume-nocopy","--user=65534:65534","--env","HOME=/tmp",
            "--workdir","/site","--entrypoint","node",d.imageId,"/site/server.mjs"));
        required(call(20,stopping,"start",server(d.id)));
        if(stopping.getAsBoolean()) throw new java.util.concurrent.CancellationException();
        int port=inspect(d);
        for(int i=0;i<12;i++) {
            if(stopping.getAsBoolean()) throw new java.util.concurrent.CancellationException();
            if(healthy(d,port)) return port;
            Thread.sleep(250);
        }
        throw new IOException("The static server did not pass its HTTP health check");
    }
    int inspect(Deployment d) throws Exception {
        JsonNode c=json.readTree(required(call(8,()->false,"inspect",server(d.id)))).get(0),h=c.path("HostConfig"),config=c.path("Config");
        boolean safe=owned(config.path("Labels"),d.id) && c.path("State").path("Running").asBoolean()
            && config.path("Image").asText().equals(d.imageId) && config.path("User").asText().equals("65534:65534")
            && config.path("Entrypoint").size()==1 && config.path("Entrypoint").get(0).asText().equals("node")
            && config.path("Cmd").size()==1 && config.path("Cmd").get(0).asText().equals("/site/server.mjs")
            && h.path("NetworkMode").asText().equals("bridge") && h.path("ReadonlyRootfs").asBoolean() && !h.path("Privileged").asBoolean(true)
            && h.path("Memory").asLong()==134217728L && h.path("NanoCpus").asLong()==500000000L && h.path("PidsLimit").asInt()==64
            && contains(h.path("CapDrop"),"ALL") && contains(h.path("SecurityOpt"),"no-new-privileges")
            && h.path("Binds").size()==0 && h.path("Tmpfs").path("/tmp").asText().equals("rw,noexec,nosuid,nodev,size=32m")
            && h.path("RestartPolicy").path("Name").asText().equals("no")
            && h.path("LogConfig").path("Type").asText().equals("json-file")
            && h.path("LogConfig").path("Config").path("max-size").asText().equals("1m") && h.path("LogConfig").path("Config").path("max-file").asText().equals("1");
        int mounts=0;
        for(JsonNode mount:c.path("Mounts")) {
            if(mount.path("Destination").asText().equals("/tmp") && mount.path("Type").asText().equals("tmpfs")) continue;
            mounts++;safe &= mount.path("Type").asText().equals("volume") && mount.path("Name").asText().equals(volume(d.id)) && mount.path("Destination").asText().equals("/site") && !mount.path("RW").asBoolean(true);
        }
        JsonNode ports=c.path("NetworkSettings").path("Ports"),binding=ports.path("8080/tcp");
        safe &= mounts==1 && ports.size()==1 && binding.isArray() && binding.size()==1;
        int port=binding.path(0).path("HostPort").asInt();
        safe &= binding.path(0).path("HostIp").asText().equals("127.0.0.1") && port>=1024 && port<=65535;
        if(!safe) throw new IOException("Deployment isolation could not be verified");
        return port;
    }
    public boolean healthy(Deployment d,int port) {
        if(port<1024 || port>65535) return false;
        try {
            var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/__jarvis_health")).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var body=response.body()) {
                var read=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return body.readNBytes(4097);}catch(IOException e){throw new java.io.UncheckedIOException(e);}});
                byte[] bytes;
                try{bytes=read.get(2,java.util.concurrent.TimeUnit.SECONDS);}finally{if(!read.isDone()){read.cancel(true);body.close();}}
                if(response.statusCode()!=200 || bytes.length>4096) return false;
                JsonNode result=json.readTree(bytes);
                return result.path("id").asText().equals(d.id.toString()) && result.path("commit").asText().equals(d.commitId) && result.path("artifact").asText().equals(d.artifactHash) && result.path("expiresAt").asText().equals(d.expiresAt.toString());
            }
        } catch(Exception e) {return false;}
    }
    public boolean verifyLive(Deployment d) {try{return d.port!=null && inspect(d)==d.port && healthy(d,d.port);}catch(Exception e){return false;}}
    public String logs(UUID id) {
        try {
            JsonNode c=json.readTree(required(call(5,()->false,"inspect",server(id)))).get(0);
            if(!owned(c.path("Config").path("Labels"),id)) return "Logs unavailable: deployment ownership did not match.";
            return PlanContext.redact(required(call(5,()->false,"logs","--tail","100",server(id))));
        } catch(Exception e){return "Deployment logs unavailable. Check Docker Desktop.";}
    }
    public boolean cleanup(UUID id) {
        boolean clean=true;
        for(String name:List.of(server(id),loader(id))) {
            try {
                var result=call(5,()->false,"container","inspect",name);
                if(result.exitCode()!=0){if(!missing(result.output())) clean=false;continue;}
                if(!owned(json.readTree(result.output()).get(0).path("Config").path("Labels"),id)){clean=false;continue;}
                if(call(10,()->false,"rm","--force",name).exitCode()!=0) clean=false;
            } catch(Exception e){clean=false;}
        }
        try {
            var result=call(5,()->false,"volume","inspect",volume(id));
            if(result.exitCode()==0) {
                if(!owned(json.readTree(result.output()).get(0).path("Labels"),id) || call(10,()->false,"volume","rm",volume(id)).exitCode()!=0) clean=false;
            } else if(!missing(result.output())) clean=false;
        } catch(Exception e){clean=false;}
        return clean;
    }
    private boolean missing(String text){String value=text.toLowerCase(Locale.ROOT);return value.contains("no such container") || value.contains("no such object") || value.contains("no such volume");}
    private boolean contains(JsonNode values,String value){for(JsonNode node:values)if(node.asText().equals(value)||node.asText().equals(value+":true"))return true;return false;}
    private boolean owned(JsonNode labels,UUID id){return labels.path("jarvis.owner").asText().equals(owner)&&labels.path("jarvis.deployment").asText().equals(id.toString());}
    private String server(UUID id){return "jarvis-web-"+id;}
    private String loader(UUID id){return "jarvis-web-load-"+id;}
    private String volume(UUID id){return "jarvis-web-source-"+id;}
    private String required(BoundedProcess.Result result)throws IOException{if(result.exitCode()!=0 || result.output().length()>=65536)throw new IOException("Docker deployment command failed or exceeded its output limit");return result.output();}
    private BoundedProcess.Result call(int seconds,BooleanSupplier cancelled,String...args)throws Exception{
        List<String> command=new ArrayList<>(List.of(executable,"--context",context));command.addAll(List.of(args));return processes.run(command,null,Duration.ofSeconds(seconds),cancelled,s->{});
    }
}
