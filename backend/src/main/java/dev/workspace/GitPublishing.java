package dev.workspace;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Builds a commit from the original full tree in private storage, preserving unrelated source and history. */
@Service
public class GitPublishing {
    public record Input(String account,String repository,String authorName,String authorEmail,String message) {}
    private final GitPublicationRepository publications;
    private final ExecutionStore executions;
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final ActivityRepository activity;
    private final CandidateWorkspace workspaces;
    private final ChangeDocument changes;
    private final GitHubTransport github;
    private final Path root;
    private final boolean enabled;
    public GitPublishing(GitPublicationRepository publications,ExecutionStore executions,TaskRepository tasks,ProjectRepository projects,ActivityRepository activity,
            CandidateWorkspace workspaces,ChangeDocument changes,GitHubTransport github,@Value("${workspace.platform-root}") String platform,
            @Value("${workspace.git.enabled:false}") boolean enabled) throws Exception {
        this.publications=publications;this.executions=executions;this.tasks=tasks;this.projects=projects;this.activity=activity;
        this.workspaces=workspaces;this.changes=changes;this.github=github;this.enabled=enabled;
        root=Path.of(platform).toRealPath().resolve(".runtime/publications");Files.createDirectories(root);
    }
    public boolean enabled() {return enabled;}
    public Optional<GitPublication> get(UUID id) {return publications.findById(id);}
    private void requireEnabled() {if(!enabled) throw WorkspaceFiles.bad("Managed-project Git publishing is disabled for this session.");}
    private Path path(UUID id) {return root.resolve(id.toString());}
    private Path candidate(UUID id) {return root.getParent().resolve("executions").resolve(id.toString()).resolve("candidate");}
    private Project project(ExecutionAttempt a) {return projects.findById(tasks.findById(a.taskId).orElseThrow().projectId).orElseThrow();}
    private CandidateWorkspace.Snapshot validate(ExecutionAttempt a) throws Exception {
        if(!a.status.equals("PASSED") || a.cleanupPending || a.exitCode==null || a.exitCode!=0 || a.testCount==null || a.testCount<1) throw WorkspaceFiles.bad("Only a passing, cleaned-up candidate may be committed.");
        var source=workspaces.inspect(project(a));
        if(!source.fingerprint().equals(a.sourceFingerprint) || !source.commit().equals(a.baseCommit)
            || !workspaces.candidateHash(candidate(a.id)).equals(a.candidateFingerprint) || !workspaces.diff(candidate(a.id)).equals(a.diff))
            throw WorkspaceFiles.bad("Source or candidate changed since checks. Prepare and check a fresh candidate.");
        return source;
    }
    public synchronized GitPublication prepare(UUID id,Input input) throws Exception {
        requireEnabled();validateInput(input);
        var old=publications.findById(id);
        if(old.isPresent() && !old.get().commitId.isBlank()) throw WorkspaceFiles.bad("This candidate already has a recorded commit. Review or retry its existing publication.");
        ExecutionAttempt a=executions.get(id);var source=validate(a);Path original=Path.of(project(a).directory),privateRoot=path(id);
        long size=Long.parseLong(workspaces.git(original,"rev-list","--disk-usage","--objects",source.commit()).strip());
        if(size>50L*1024*1024) throw WorkspaceFiles.bad("This initial Git profile supports at most 50 MB of reachable history.");
        Files.createDirectories(privateRoot);workspaces.git(privateRoot,"init","--quiet");
        String objectPath=workspaces.git(original,"rev-parse","--path-format=absolute","--git-path","objects").strip();
        Path objects=Path.of(objectPath).toRealPath();
        if(objectPath.contains("\n") || objectPath.contains("\r")) throw WorkspaceFiles.bad("Unsupported Git object directory.");
        Path alternates=privateRoot.resolve(".git/objects/info/alternates");Files.createDirectories(alternates.getParent());
        Files.writeString(alternates,objects.toString().replace('\\','/')+"\n");
        workspaces.git(privateRoot,"read-tree",source.commit());
        var selection=tools.jackson.databind.json.JsonMapper.builder().build().readTree(a.selectedPaths);
        Set<String> allowed=new HashSet<>();
        for(var field:List.of("files","newFiles")) for(var value:selection.path(field)) allowed.add(value.asText());
        var proposal=changes.validate(a.proposal,allowed,source.contents().keySet());
        var checkedContents=workspaces.contents(candidate(id));
        for(var change:proposal.changes()) {
            if(change.operation().equals("delete")) workspaces.git(privateRoot,"update-index","--force-remove","--",change.path());
            else {
                String mode="100644";
                String entry=workspaces.git(privateRoot,"ls-tree",source.commit(),"--",change.path());
                if(!entry.isBlank()) mode=entry.substring(0,6);
                if(!Set.of("100644","100755").contains(mode)) throw WorkspaceFiles.bad("Unsupported source mode.");
                if(!change.content().equals(checkedContents.get(change.path()))) throw WorkspaceFiles.bad("The checked proposal and candidate differ.");
                Path staged=privateRoot.resolve("staged");workspaces.write(staged,change.path(),change.content());
                String blob=workspaces.git(privateRoot,"hash-object","--no-filters","-w","--",staged.resolve(change.path()).toString()).strip();
                workspaces.git(privateRoot,"update-index","--add","--cacheinfo",mode,blob,change.path());
            }
        }
        GitPublication p=new GitPublication(id);p.projectId=project(a).id;p.baseCommit=source.commit();
        p.account=input.account();p.repository=input.repository();p.authorName=input.authorName();p.authorEmail=input.authorEmail();p.message=input.message();
        p.branch="codex/task-"+a.taskId.toString().substring(0,8)+"-"+id.toString().substring(0,8);
        p.treeId=workspaces.git(privateRoot,"write-tree").strip();
        p.diff=workspaces.git(privateRoot,"diff","--no-ext-diff","--no-textconv","--binary",p.baseCommit,p.treeId,"--");
        if(p.diff.isBlank() || p.diff.length()>60000) throw WorkspaceFiles.bad("The commit diff is empty or too large.");
        p.reviewHash=hash(p);return save(p,null);
    }
    private String hash(GitPublication p) throws Exception {
        return CandidateWorkspace.fingerprint(p.id.toString(),Map.of("base",p.baseCommit,"tree",p.treeId,"diff",p.diff,"account",p.account,"repository",p.repository,
            "author",p.authorName+" <"+p.authorEmail+">","message",p.message,"branch",p.branch));
    }
    public synchronized GitPublication commit(UUID id,String reviewedHash) throws Exception {
        requireEnabled();GitPublication p=publications.findById(id).orElseThrow();
        if(!p.status.equals("PREPARED") || !p.reviewHash.equals(reviewedHash) || !hash(p).equals(reviewedHash)) throw WorkspaceFiles.bad("Review the prepared commit again.");
        validate(executions.get(id));Path privateRoot=path(id);
        if(!workspaces.git(privateRoot,"diff","--no-ext-diff","--no-textconv","--binary",p.baseCommit,p.treeId,"--").equals(p.diff)) throw WorkspaceFiles.bad("The prepared tree changed.");
        p.status="COMMITTING";save(p,null);
        try {
            p.commitId=workspaces.git(privateRoot,"-c","user.name="+p.authorName,"-c","user.email="+p.authorEmail,"-c","commit.gpgSign=false","commit-tree",p.treeId,"-p",p.baseCommit,"-m",p.message).strip();
            workspaces.git(privateRoot,"update-ref","refs/heads/"+p.branch,p.commitId);
            workspaces.git(privateRoot,"repack","-a","-d");
            Files.deleteIfExists(privateRoot.resolve(".git/objects/info/alternates"));
            workspaces.git(privateRoot,"fsck","--connectivity-only","--no-reflogs");
            p.pushHash=CandidateWorkspace.fingerprint(p.commitId,Map.of("remote",p.remoteUrl(),"branch",p.branch));
            p.status="COMMITTED";return save(p,"Checked candidate committed on private branch "+p.branch+": "+p.commitId);
        } catch(Exception e) {p.status="FAILED";p.error="Commit preparation did not finish. Retained private data requires review; no push was started.";save(p,null);throw e;}
    }
    public synchronized GitPublication push(UUID id,String reviewedHash) throws Exception {
        requireEnabled();GitPublication p=publications.findById(id).orElseThrow();
        if(!Set.of("COMMITTED","PUSH_UNKNOWN","PUSHED").contains(p.status) || !p.pushHash.equals(reviewedHash) || !p.pushHash.equals(CandidateWorkspace.fingerprint(p.commitId,Map.of("remote",p.remoteUrl(),"branch",p.branch))))
            throw WorkspaceFiles.bad("Review the exact commit and destination before pushing.");
        Path privateRoot=path(id);
        if(!workspaces.git(privateRoot,"rev-parse","refs/heads/"+p.branch).strip().equals(p.commitId)
            || !workspaces.git(privateRoot,"rev-parse",p.commitId+"^{tree}").strip().equals(p.treeId)) throw WorkspaceFiles.bad("The private commit changed.");
        github.verifyAccount(p);p.status="PUSHING";p.error="";save(p,null);
        try {
            String remote=github.remoteCommit(privateRoot,p);
            if(!remote.isBlank() && !remote.equals(p.commitId)) throw WorkspaceFiles.bad("The destination branch already contains another commit. It will not be overwritten.");
            if(remote.isBlank()) github.push(privateRoot,p);
            if(!github.remoteCommit(privateRoot,p).equals(p.commitId)) throw WorkspaceFiles.bad("GitHub did not confirm the pushed commit.");
            p.status="PUSHED";return save(p,"Candidate commit verified on GitHub: "+p.account+"/"+p.repository+" "+p.branch+" "+p.commitId);
        } catch(Exception e) {p.status="PUSH_UNKNOWN";p.error="Push not confirmed. Review the destination and retry to reconcile its exact branch; no force push is used.";save(p,null);throw e;}
    }
    private GitPublication save(GitPublication p,String message) {
        p.updatedAt=Instant.now();GitPublication saved=publications.saveAndFlush(p);
        if(message!=null) activity.save(new Activity(p.projectId,executions.get(p.id).taskId,message));return saved;
    }
    @EventListener(ApplicationReadyEvent.class) public void recover() {
        for(GitPublication p:publications.findAll()) {
            if(p.status.equals("PUSHING")) {p.status="PUSH_UNKNOWN";p.error="The backend stopped during push. Review and retry to reconcile the remote branch.";save(p,null);}
            else if(p.status.equals("COMMITTING")) {p.status="FAILED";p.error="Commit was interrupted. Private artifacts were retained; no push was started.";save(p,null);}
        }
    }
    static void validateInput(Input p) {
        if(p==null || p.account()==null || !p.account().matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")
            || p.repository()==null || !p.repository().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}") || p.repository().endsWith(".git")
            || !text(p.authorName(),100) || p.authorName().contains("<") || p.authorName().contains(">")
            || p.authorEmail()==null || !p.authorEmail().matches("[A-Za-z0-9._+%-]+@[A-Za-z0-9.-]+") || p.authorEmail().length()>200
            || !text(p.message(),500)) throw WorkspaceFiles.bad("Enter a GitHub account/repository, single-line author name/email and commit message. URLs or credentials are not accepted.");
    }
    private static boolean text(String value,int max) {return value!=null && !value.isBlank() && value.length()<=max && value.chars().noneMatch(c->Character.isISOControl(c));}
}
