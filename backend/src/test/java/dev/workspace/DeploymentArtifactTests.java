package dev.workspace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class DeploymentArtifactTests {
    @TempDir Path root;
    CandidateWorkspace git;DeploymentArtifact artifacts;GitPublication publication;Path repository;
    @BeforeEach void setup()throws Exception {
        Path platform=Files.createDirectory(root.resolve("platform"));WorkspaceFiles files=new WorkspaceFiles(platform.toString());git=new CandidateWorkspace(files,new BoundedProcess());
        artifacts=new DeploymentArtifact(git,files,platform.toString());publication=new GitPublication(UUID.randomUUID());
        repository=platform.resolve(".runtime/publications").resolve(publication.id.toString());Files.createDirectories(repository.resolve("public"));
        Files.writeString(repository.resolve("public/index.html"),"<!doctype html><title>Fixture</title><p>Checked artifact</p>\n");
        Files.writeString(repository.resolve("public/app.js"),"console.log('fixture');\n");git.git(repository,"init","--quiet");commit();
    }
    void commit()throws Exception {git.git(repository,"add","--all");git.git(repository,"-c","user.name=Fixture","-c","user.email=fixture@localhost","-c","commit.gpgSign=false","commit","--quiet","-m","Artifact fixture");publication.commitId=git.git(repository,"rev-parse","HEAD").strip();publication.treeId=git.git(repository,"rev-parse","HEAD^{tree}").strip();}
    @Test void artifactReadsTheRecordedCommitRatherThanUnfinishedWorkingFiles()throws Exception {
        var before=artifacts.read(publication);Files.writeString(repository.resolve("public/index.html"),"unfinished replacement");
        var after=artifacts.read(publication);assertThat(after.hash()).isEqualTo(before.hash());assertThat(after.manifest()).contains("index.html","app.js");assertThat(after.files().get("index.html")).contains("Checked artifact");
    }
    @Test void unsupportedAssetsAndCredentialLikeTextFailClosed()throws Exception {
        Files.writeString(repository.resolve("public/private.secret"),"not a supported asset");commit();
        assertThatThrownBy(()->artifacts.read(publication)).hasMessageContaining("unsupported");
        Files.delete(repository.resolve("public/private.secret"));Files.writeString(repository.resolve("public/app.js"),"password=fixture-value\n");commit();
        assertThatThrownBy(()->artifacts.read(publication)).hasMessageContaining("Credential-like");
    }
    @Test void missingIndexAndChangedTreeCannotProduceADeployment()throws Exception {
        Files.delete(repository.resolve("public/index.html"));commit();assertThatThrownBy(()->artifacts.read(publication)).hasMessageContaining("index.html");
        publication.treeId="f".repeat(40);assertThatThrownBy(()->artifacts.read(publication)).hasMessageContaining("tree changed");
    }
}
