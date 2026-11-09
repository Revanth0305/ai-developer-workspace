package dev.workspace;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/** Fixed GitHub HTTPS destination and CLI credential helper; no credential is stored in application data. */
@Service
public class GitHubTransport {
    private final BoundedProcess processes;
    private final String gh;
    public GitHubTransport(BoundedProcess processes,@Value("${workspace.git.gh-executable:gh}") String gh) {
        this.processes=processes;this.gh=gh;
    }
    public void verifyAccount(GitPublication p) throws Exception {
        String login=required(processes.run(List.of(gh,"api","--hostname","github.com","user","--jq",".login"),null,Duration.ofSeconds(15),()->false,s->{})).strip();
        if(!login.equalsIgnoreCase(p.account)) throw WorkspaceFiles.bad("Sign in to GitHub CLI with the explicitly selected account before pushing.");
        String name=required(processes.run(List.of(gh,"repo","view","github.com/"+p.account+"/"+p.repository,"--json","nameWithOwner","--jq",".nameWithOwner"),null,Duration.ofSeconds(15),()->false,s->{})).strip();
        if(!name.equalsIgnoreCase(p.account+"/"+p.repository)) throw WorkspaceFiles.bad("The selected repository could not be verified.");
    }
    public String remoteCommit(Path root,GitPublication p) throws Exception {
        String value=required(call(root,15,"ls-remote","--heads",p.remoteUrl(),"refs/heads/"+p.branch)).strip();
        if(value.isBlank()) return "";
        String[] fields=value.split("\\s+");
        if(fields.length!=2 || !fields[0].matches("[a-f0-9]{40,64}") || !fields[1].equals("refs/heads/"+p.branch)) throw WorkspaceFiles.bad("Remote branch state could not be verified.");
        return fields[0];
    }
    public void push(Path root,GitPublication p) throws Exception {
        required(call(root,60,"push","--porcelain","--no-verify",p.remoteUrl(),p.commitId+":refs/heads/"+p.branch));
    }
    private BoundedProcess.Result call(Path root,int seconds,String...args) throws Exception {
        // A fixed official helper is the only command permitted for network credentials.
        // Shell single quotes survive Java's Windows argv quoting for paths with spaces.
        String helper="!'"+gh.replace('\\','/').replace("'","'\\''")+"' auth git-credential";
        List<String> command=new ArrayList<>(List.of("git","-c","core.hooksPath="+BoundedProcess.nullDevice(),"-c","credential.helper=","-c","credential.helper="+helper,
            "-c","protocol.allow=never","-c","protocol.https.allow=always","-c","http.followRedirects=false","-c","push.recurseSubmodules=no","-C",root.toString()));
        command.addAll(List.of(args));return processes.run(command,root,Duration.ofSeconds(seconds),()->false,s->{});
    }
    private String required(BoundedProcess.Result value) {
        if(value.exitCode()!=0 || value.output().length()>=65536) throw WorkspaceFiles.bad("GitHub operation failed. Check CLI sign-in, repository permissions and connection; no force push was attempted.");
        return value.output();
    }
}
