package dev.workspace;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Service
public class DeploymentArtifact {
    public record Snapshot(String hash,Map<String,String> files,String manifest) {}
    private final CandidateWorkspace git;
    private final WorkspaceFiles sources;
    private final Path publications;
    public DeploymentArtifact(CandidateWorkspace git,WorkspaceFiles sources,@Value("${workspace.platform-root}") String platform) throws Exception {
        this.git=git;this.sources=sources;publications=Path.of(platform).toRealPath().resolve(".runtime/publications");
    }
    public Snapshot read(GitPublication p) throws Exception {
        if(!p.commitId.matches("[a-f0-9]{40,64}")) throw WorkspaceFiles.bad("A recorded local commit is required.");
        Path repository=publications.resolve(p.id.toString());
        if(!git.git(repository,"rev-parse",p.commitId+"^{tree}").strip().equals(p.treeId)) throw WorkspaceFiles.bad("The committed tree changed.");
        String entries=git.git(repository,"ls-tree","-r","-z",p.commitId,"--","public/");
        Map<String,String> files=new TreeMap<>();int bytes=0;
        for(String entry:entries.split("\u0000")) {
            if(entry.isEmpty()) continue;
            int tab=entry.indexOf('\t');if(tab<0 || !entry.startsWith("100644 ") && !entry.startsWith("100755 ")) throw WorkspaceFiles.bad("Static deployment does not accept links or submodules.");
            String path=entry.substring(tab+1);
            if(!path.startsWith("public/")) throw WorkspaceFiles.bad("Invalid artifact path.");
            String relative=path.substring(7);
            if(!sources.sourcePath(path) || !relative.matches("[A-Za-z0-9_-][A-Za-z0-9_./-]{0,180}\\.(?:html|css|js|mjs|json|txt)")
                || Arrays.stream(relative.split("/",-1)).anyMatch(part->part.isEmpty() || part.startsWith("."))) throw WorkspaceFiles.bad("The public directory contains an unsupported file path. Use HTML, CSS, JavaScript, JSON or text assets without hidden paths.");
            String text=git.git(repository,"show",p.commitId+":"+path);
            int size=text.getBytes(StandardCharsets.UTF_8).length;bytes+=size;
            if(size>64000 || bytes>2*1024*1024 || files.size()>=100 || text.indexOf('\0')>=0) throw WorkspaceFiles.bad("Static artifacts are limited to 100 text assets, 64 KB each and 2 MB total.");
            if(!PlanContext.redact(text).equals(text)) throw WorkspaceFiles.bad("Credential-like content was found in a public asset. Review the source before deployment.");
            files.put(relative,text);
        }
        if(!files.containsKey("index.html")) throw WorkspaceFiles.bad("The committed public/index.html is required for this static deployment profile.");
        StringBuilder manifest=new StringBuilder();
        for(var file:files.entrySet()) manifest.append(file.getKey()).append(" | ").append(file.getValue().getBytes(StandardCharsets.UTF_8).length).append(" bytes\n");
        return new Snapshot(CandidateWorkspace.fingerprint(p.commitId,files),Collections.unmodifiableMap(files),manifest.toString());
    }
    public void stage(Deployment deployment,Snapshot snapshot,Path destination) throws Exception {
        Files.createDirectories(destination);
        for(var file:snapshot.files().entrySet()) git.write(destination.resolve("public"),file.getKey(),file.getValue());
        try(var server=DeploymentArtifact.class.getResourceAsStream("/deployment-server.mjs")) {
            if(server==null) throw new IllegalStateException("Static server resource missing");
            Files.write(destination.resolve("server.mjs"),server.readAllBytes());
        }
        String config=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(Map.of("id",deployment.id.toString(),"commit",deployment.commitId,"artifact",deployment.artifactHash,"expiresAt",deployment.expiresAt.toString(),"files",snapshot.files().keySet()));
        Files.writeString(destination.resolve("deployment.json"),config,StandardCharsets.UTF_8);
    }
}
