package dev.workspace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="WORKSPACE_DOCKER_TEST_EXECUTABLE",matches=".+")
class DeploymentCompatibilityTests {
    @TempDir Path root;
    record Fixture(Deployment deployment,DockerDeployment docker,Path staging){}
    Fixture fixture()throws Exception {
        Path platform=Files.createDirectory(root.resolve("platform"));WorkspaceFiles files=new WorkspaceFiles(platform.toString());CandidateWorkspace git=new CandidateWorkspace(files,new BoundedProcess());
        DeploymentArtifact artifacts=new DeploymentArtifact(git,files,platform.toString());String executable=System.getenv("WORKSPACE_DOCKER_TEST_EXECUTABLE");
        DockerSandbox runtime=new DockerSandbox(new BoundedProcess(),true,executable,"",platform.toString());var status=runtime.status(true);assertThat(status.available()).as(status.message()).isTrue();
        Deployment d=new Deployment();d.commitId="a".repeat(40);d.imageId=status.imageId();d.expiresAt=Instant.now().plusSeconds(1800);
        Map<String,String> contents=Map.of("index.html","<!doctype html><title>CP7 fixture</title><p>CP7 real static fixture</p>\n","app.js","console.log('fixture');\n");
        d.artifactHash=CandidateWorkspace.fingerprint(d.commitId,contents);Path staging=root.resolve("staging");artifacts.stage(d,new DeploymentArtifact.Snapshot(d.artifactHash,contents,"index.html\napp.js\n"),staging);
        return new Fixture(d,new DockerDeployment(new BoundedProcess(),executable,"",platform.toString()),staging);
    }
    HttpResponse<String> request(int port,String path,String method)throws Exception {return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(3)).method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());}
    @Test void realContainerServesVerifiedAssetsWithLocalBindingAndReadOnlyPolicy()throws Exception {
        var f=fixture();Deployment d=f.deployment();
        try {
            int port=f.docker().launch(d,f.staging(),()->false);d.port=port;
            assertThat(f.docker().verifyLive(d)).isTrue();assertThat(request(port,"/","GET").body()).contains("CP7 real static fixture");
            assertThat(request(port,"/server.mjs","GET").statusCode()).isEqualTo(404);assertThat(request(port,"/","POST").statusCode()).isEqualTo(405);
            assertThat(request(port,"/app.js","GET").headers().firstValue("Content-Security-Policy").orElse("")).contains("connect-src 'none'","frame-ancestors 'none'");
            var denied=new BoundedProcess().run(List.of(System.getenv("WORKSPACE_DOCKER_TEST_EXECUTABLE"),"--context",System.getProperty("os.name").startsWith("Windows")?"desktop-linux":"default","exec","jarvis-web-"+d.id,"node","-e","const fs=require('node:fs'),assert=require('node:assert/strict');assert.throws(()=>fs.writeFileSync('/site/escape','no'));assert.throws(()=>fs.writeFileSync('/etc/escape','no'));assert.equal(fs.existsSync('/var/run/docker.sock'),false);assert.equal(fs.existsSync('/root/.codex/auth.json'),false);"),null,Duration.ofSeconds(10),()->false,s->{});
            assertThat(denied.exitCode()).isZero();assertThat(f.docker().logs(d.id)).contains("Static deployment ready","HTTP 200");
        } finally {assertThat(f.docker().cleanup(d.id)).isTrue();}
    }
    @Test void copiedArtifactTamperingCannotPassTheHttpHealthGate()throws Exception {
        var f=fixture();Files.writeString(f.staging().resolve("public/index.html"),"tampered after review");
        try {assertThatThrownBy(()->f.docker().launch(f.deployment(),f.staging(),()->false)).isInstanceOf(Exception.class);}
        finally {assertThat(f.docker().cleanup(f.deployment().id)).isTrue();}
    }
}
