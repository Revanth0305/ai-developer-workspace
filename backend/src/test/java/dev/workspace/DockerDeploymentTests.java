package dev.workspace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class DockerDeploymentTests {
    @TempDir Path root;
    @Test void cleanupDoesNotRemoveContainersWithDifferentOwnership()throws Exception {
        BoundedProcess processes=mock(BoundedProcess.class);
        when(processes.run(anyList(),isNull(),any(),any(),any())).thenAnswer(call->{List<String> command=call.getArgument(0);if(command.contains("container"))return new BoundedProcess.Result(0,"[{\"Config\":{\"Labels\":{\"jarvis.owner\":\"another-application\"}}}]");return new BoundedProcess.Result(1,"No such volume");});
        DockerDeployment docker=new DockerDeployment(processes,"docker","default",root.toString());
        assertThat(docker.cleanup(UUID.randomUUID())).isFalse();
        verify(processes,never()).run(argThat(command->command.contains("rm")),any(),any(),any(),any());
    }
    @Test void healthDoesNotProbePrivilegedOrInvalidPorts()throws Exception {
        BoundedProcess processes=mock(BoundedProcess.class);DockerDeployment docker=new DockerDeployment(processes,"docker","default",root.toString());
        assertThat(docker.healthy(new Deployment(),80)).isFalse();assertThat(docker.healthy(new Deployment(),70000)).isFalse();verifyNoInteractions(processes);
    }
}
