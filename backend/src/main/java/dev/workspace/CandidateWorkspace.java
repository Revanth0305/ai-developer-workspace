package dev.workspace;

import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Copies bounded, tracked text into an independent local repository; never changes the source. */
@Service
public class CandidateWorkspace {
    public record Snapshot(String commit, String fingerprint, Map<String,String> contents) {}
    private final WorkspaceFiles files;
    private final BoundedProcess processes;
    public CandidateWorkspace(WorkspaceFiles files, BoundedProcess processes) { this.files=files; this.processes=processes; }

    public Snapshot inspect(Project project) throws Exception {
        Path root=files.registerDirectory(project.directory);
        if(!Path.of(git(root,"rev-parse","--show-toplevel").strip()).toRealPath().equals(root)) throw WorkspaceFiles.bad("Execution requires the registered repository root.");
        String autocrlf=processes.gitSetting(root,"core.autocrlf"),eol=processes.gitSetting(root,"core.eol");
        if(!Set.of("true","false","input").contains(autocrlf)) autocrlf="false";
        if(!Set.of("lf","crlf","native").contains(eol)) eol="native";
        String commit=git(root,"rev-parse","--verify","HEAD").strip();
        if(!commit.matches("[a-f0-9]{40,64}")) throw WorkspaceFiles.bad("The project needs an existing Git commit.");
        // A full content diff avoids false dirty statuses after CRLF-only stat changes.
        // Check the index separately so a staged change hidden by a working-tree reversal is retained.
        if(!git(root,"-c","core.autocrlf="+autocrlf,"-c","core.eol="+eol,"diff","--no-ext-diff","--no-textconv","HEAD","--").isBlank()
                || !git(root,"diff","--cached","--no-ext-diff","--no-textconv","HEAD","--").isBlank()
                || !git(root,"ls-files","--others","--exclude-standard","-z").isBlank())
            throw WorkspaceFiles.bad("Commit or stash unfinished changes in this managed project before preparing a candidate.");
        String entries=git(root,"ls-files","--stage","-z");
        Map<String,String> contents=new TreeMap<>(); int total=0;
        for(String entry:entries.split("\u0000")) {
            if(entry.isEmpty()) continue;
            int tab=entry.indexOf('\t'); if(tab<0) throw WorkspaceFiles.bad("Invalid tracked source entry.");
            String metadata=entry.substring(0,tab),path=entry.substring(tab+1);
            if(!metadata.startsWith("100644 ") && !metadata.startsWith("100755 ")) throw WorkspaceFiles.bad("CP5 does not support symlinks or submodules in managed repositories.");
            if(!files.sourcePath(path)) continue;
            String content=files.read(project.directory,path).get("content");
            total+=content.getBytes(StandardCharsets.UTF_8).length;
            if(contents.size()>=250 || total>4*1024*1024) throw WorkspaceFiles.bad("Candidate snapshots are limited to 250 text files and 4 MB.");
            contents.put(path.replace('\\','/'),content);
        }
        if(contents.isEmpty()) throw WorkspaceFiles.bad("No supported tracked source files were found.");
        return new Snapshot(commit,fingerprint(commit,contents),Collections.unmodifiableMap(contents));
    }
    public void create(Path candidate,Snapshot snapshot) throws Exception {
        Files.createDirectories(candidate);
        try(var entries=Files.list(candidate)) { if(entries.findAny().isPresent()) throw new IOException("Candidate directory is not empty"); }
        for(var entry:snapshot.contents().entrySet()) write(candidate,entry.getKey(),entry.getValue());
        git(candidate,"init","--quiet");
        git(candidate,"add","--all");
        git(candidate,"-c","user.name=Codex","-c","user.email=codex@localhost","-c","commit.gpgSign=false","commit","--quiet","-m","Isolated CP5 source snapshot");
    }
    public void write(Path candidate,String path,String content) throws IOException {
        Path target=candidate.resolve(path).normalize();
        if(!target.startsWith(candidate) || !files.sourcePath(path)) throw new IOException("Invalid candidate path");
        Files.createDirectories(target.getParent());
        if(!target.getParent().toRealPath().startsWith(candidate.toRealPath()) || Files.isSymbolicLink(target)) throw new IOException("Candidate path escaped its directory");
        Files.writeString(target,content,StandardCharsets.UTF_8);
    }
    public void apply(Path candidate,List<ChangeDocument.Change> changes) throws Exception {
        for(var change:changes) {
            Path target=candidate.resolve(change.path()).normalize();
            if(!target.startsWith(candidate) || !files.sourcePath(change.path())) throw WorkspaceFiles.bad("Invalid proposed candidate path.");
            if(change.operation().equals("delete")) Files.delete(target);
            else write(candidate,change.path(),change.content());
        }
        git(candidate,"add","--intent-to-add","--all");
    }
    public String diff(Path candidate) throws Exception {
        String diff=git(candidate,"diff","--no-ext-diff","--no-textconv","--binary","HEAD","--");
        if(diff.length()>60000) throw WorkspaceFiles.bad("The candidate diff is too large for review.");
        return diff;
    }
    public Map<String,String> contents(Path candidate) throws IOException {
        Map<String,String> result=new TreeMap<>();
        try(var walk=Files.walk(candidate)) {
            for(Path path:walk.filter(p -> !p.equals(candidate)).toList()) {
                Path relative=candidate.relativize(path);
                if(relative.getName(0).toString().equals(".git")) continue;
                if(Files.isSymbolicLink(path) || !path.toRealPath().startsWith(candidate.toRealPath())) throw new IOException("Candidate contains a link");
                if(Files.isDirectory(path)) continue;
                String name=relative.toString().replace('\\','/');
                if(!files.sourcePath(name) || Files.size(path)>65536 || result.size()>=258) throw new IOException("Candidate contains an unsupported file");
                result.put(name,Files.readString(path,StandardCharsets.UTF_8));
            }
        }
        return result;
    }
    public String candidateHash(Path candidate) throws Exception { return fingerprint("candidate",contents(candidate)); }
    public void export(Path candidate,Path staging) throws Exception {
        Files.createDirectories(staging);
        for(var entry:contents(candidate).entrySet()) write(staging,entry.getKey(),entry.getValue());
    }
    public String git(Path root,String... args) throws Exception {
        List<String> command=new ArrayList<>(List.of("git","--no-optional-locks","-c","core.hooksPath="+BoundedProcess.nullDevice(),"-c","core.fsmonitor=false","-c","core.autocrlf=false","-c","core.safecrlf=false","-c","init.templateDir=","-C",root.toString()));
        // Git status/hash operations can invoke configured clean filters. Disable every driver
        // before inspecting an untrusted repository, not just hooks and fsmonitor.
        List<String> probe=new ArrayList<>(command);probe.addAll(List.of("config","--null","--name-only","--list"));
        var config=processes.run(probe,root,Duration.ofSeconds(8),()->false,s->{});
        if(config.exitCode()!=0 || config.output().length()>=65536) throw WorkspaceFiles.bad("Git configuration could not be inspected safely.");
        Set<String> drivers=new HashSet<>();
        for(String key:config.output().split("\u0000")) {
            var match=java.util.regex.Pattern.compile("(?i)^filter\\.(.+)\\.(?:clean|smudge|process|required)$").matcher(key);
            if(match.matches()) drivers.add(match.group(1));
        }
        if(drivers.size()>20) throw WorkspaceFiles.bad("Too many Git filter drivers for bounded source inspection.");
        for(String driver:drivers) for(String option:List.of("clean","smudge","process","required"))
            command.addAll(List.of("-c","filter."+driver+"."+option+"="+(option.equals("required")?"false":"")));
        command.addAll(List.of(args));
        var result=processes.run(command,root,Duration.ofSeconds(15),()->false,s->{});
        if(result.exitCode()!=0 || result.output().length()>=65536) throw WorkspaceFiles.bad("Git snapshot inspection failed or exceeded its output limit.");
        return result.output();
    }
    public static String fingerprint(String prefix,Map<String,String> contents) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256"); digest.update(prefix.getBytes(StandardCharsets.UTF_8));
        for(var entry:new TreeMap<>(contents).entrySet()) {
            byte[] key=entry.getKey().getBytes(StandardCharsets.UTF_8),value=entry.getValue().getBytes(StandardCharsets.UTF_8);
            digest.update(java.nio.ByteBuffer.allocate(8).putInt(key.length).putInt(value.length).array());digest.update(key);digest.update(value);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
