package dev.workspace;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CandidateWorkspaceTests {
    @TempDir Path root;
    CandidateWorkspace workspaces;
    Project project;
    @BeforeEach void setup() throws Exception {
        Path platform=Files.createDirectory(root.resolve("platform")),source=Files.createDirectory(root.resolve("source"));
        WorkspaceFiles files=new WorkspaceFiles(platform.toString());workspaces=new CandidateWorkspace(files,new BoundedProcess());
        project=new Project("Sample",source.toString(),"");
        Files.writeString(source.resolve("greeting.js"),"export const greeting = () => 'Hello';\n");
        workspaces.git(source,"init","--quiet");workspaces.git(source,"add","--all");
        workspaces.git(source,"-c","user.name=Fixture","-c","user.email=fixture@localhost","-c","commit.gpgSign=false","commit","--quiet","-m","Initial fixture");
    }
    @Test void independentCandidateHasAReviewableDiffAndSourceRemainsUnchanged() throws Exception {
        var snapshot=workspaces.inspect(project);Path candidate=root.resolve("candidate");workspaces.create(candidate,snapshot);
        workspaces.apply(candidate,List.of(new ChangeDocument.Change("greeting.js","write","export const greeting = () => 'Validated';\n"),new ChangeDocument.Change("test/new.test.js","write","// new test\n")));
        assertThat(workspaces.diff(candidate)).contains("Validated","test/new.test.js");
        assertThat(Files.readString(Path.of(project.directory).resolve("greeting.js"))).contains("'Hello'");
        assertThat(workspaces.inspect(project).fingerprint()).isEqualTo(snapshot.fingerprint());
        Path staging=root.resolve("staging");workspaces.export(candidate,staging);
        assertThat(Files.exists(staging.resolve(".git"))).isFalse();
        assertThat(workspaces.candidateHash(staging)).isEqualTo(workspaces.candidateHash(candidate));
    }
    @Test void dirtyAndUntrackedSourceAreRejectedWithoutLosingChanges() throws Exception {
        Files.writeString(Path.of(project.directory).resolve("greeting.js"),"unfinished change");
        assertThatThrownBy(() -> workspaces.inspect(project)).hasMessageContaining("Commit or stash");
        assertThat(Files.readString(Path.of(project.directory).resolve("greeting.js"))).isEqualTo("unfinished change");
    }
    @Test void cleanWindowsLineEndingsArePreservedInTheSnapshot() throws Exception {
        Path source=Path.of(project.directory);workspaces.git(source,"config","core.autocrlf","true");
        String text=Files.readString(source.resolve("greeting.js")).replace("\n","\r\n");Files.writeString(source.resolve("greeting.js"),text);
        assertThat(new BoundedProcess().gitSetting(source,"core.autocrlf")).isEqualTo("true");
        assertThat(workspaces.git(source,"-c","core.autocrlf=true","diff","--no-ext-diff","--no-textconv","HEAD","--")).isBlank();
        assertThat(workspaces.inspect(project).contents().get("greeting.js")).isEqualTo(text);
    }
    @Test void repositoryCleanFiltersCannotExecuteDuringInspection() throws Exception {
        Path source=Path.of(project.directory),marker=root.resolve("filter-executed.txt");
        Files.writeString(source.resolve(".gitattributes"),"*.js filter=fixture\n");workspaces.git(source,"add","--all");
        workspaces.git(source,"-c","user.name=Fixture","-c","user.email=fixture@localhost","-c","commit.gpgSign=false","commit","--quiet","-m","Attributes fixture");
        String script="node -e \"require('fs').writeFileSync('"+marker.toString().replace('\\','/')+"','unsafe')\"";
        workspaces.git(source,"config","filter.fixture.clean",script);
        Files.setLastModifiedTime(source.resolve("greeting.js"),FileTime.fromMillis(System.currentTimeMillis()+2000));
        workspaces.inspect(project);
        assertThat(Files.exists(marker)).isFalse();
    }
    @Test void exportedCandidateRejectsLinksAndUnsupportedOrTamperedFiles() throws Exception {
        Path candidate=root.resolve("candidate");workspaces.create(candidate,workspaces.inspect(project));
        String hash=workspaces.candidateHash(candidate);Files.writeString(candidate.resolve("greeting.js"),"changed after review");
        assertThat(workspaces.candidateHash(candidate)).isNotEqualTo(hash);
        Files.writeString(candidate.resolve(".env"),"sensitive=fixture");
        assertThatThrownBy(() -> workspaces.contents(candidate)).hasMessageContaining("unsupported");
    }
    @Test void changeDocumentsRejectUnauthorizedTraversalSecretsDuplicateAndInvalidDeletes() throws Exception {
        ChangeDocument documents=new ChangeDocument(new WorkspaceFiles(root.toString()));
        String valid="{\"summary\":\"Change source\",\"changes\":[{\"path\":\"greeting.js\",\"operation\":\"write\",\"content\":\"new text\"}]}";
        assertThat(documents.validate(valid,Set.of("greeting.js"),Set.of("greeting.js")).changes()).hasSize(1);
        for(String path:List.of("../outside.js",".git/config","unauthorized.js","C:/outside.js"))
            assertThatThrownBy(() -> documents.validate(valid.replace("greeting.js",path),Set.of("greeting.js"),Set.of("greeting.js"))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> documents.validate(valid.replace("new text","api_key=fixture-value"),Set.of("greeting.js"),Set.of("greeting.js"))).hasMessageContaining("credential");
        assertThatThrownBy(() -> documents.validate(valid.replace("write","delete"),Set.of("greeting.js"),Set.of())).hasMessageContaining("existing");
    }
}
