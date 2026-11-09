package dev.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DockerSandboxTests {
    @TempDir Path root;
    final String image="sha256:"+"a".repeat(64);
    @Test void runtimeRejectsRemoteEndpointsAndNeverRunsFallbackHostCommands() throws Exception {
        BoundedProcess processes=mock(BoundedProcess.class);
        when(processes.run(anyList(),isNull(),any(),any(),any())).thenReturn(new BoundedProcess.Result(0,"tcp://untrusted:2375\n"));
        DockerSandbox sandbox=new DockerSandbox(processes,true,"docker","default",root.toString());
        assertThat(sandbox.status(true).available()).isFalse();
        verify(processes,times(1)).run(anyList(),isNull(),any(),any(),any());
    }
    @Test void isolationValidationRejectsWritableOrHostBindMounts() throws Exception {
        BoundedProcess processes=mock(BoundedProcess.class);DockerSandbox sandbox=new DockerSandbox(processes,true,"docker","default",root.toString());
        UUID id=UUID.randomUUID();
        when(processes.run(anyList(),isNull(),any(),any(),any())).thenReturn(new BoundedProcess.Result(0,"[{\"Config\":{\"User\":\"0\"},\"HostConfig\":{\"Privileged\":true},\"Mounts\":[{\"Type\":\"bind\",\"Source\":\"/\",\"RW\":true}]}]"));
        assertThatThrownBy(() -> sandbox.verifyIsolation("runner","volume",id)).hasMessageContaining("isolation");
    }
    @Test void cleanupNeverDeletesResourcesWithDifferentOwnershipLabels() throws Exception {
        BoundedProcess processes=mock(BoundedProcess.class);DockerSandbox sandbox=new DockerSandbox(processes,true,"docker","default",root.toString());
        when(processes.run(anyList(),isNull(),any(),any(),any())).thenReturn(new BoundedProcess.Result(0,"[{\"Config\":{\"Labels\":{}},\"Labels\":{}}]"));
        assertThat(sandbox.cleanup(UUID.randomUUID())).isFalse();
        var commands=org.mockito.ArgumentCaptor.forClass(List.class);
        verify(processes,times(3)).run(commands.capture(),isNull(),any(),any(),any());
        assertThat(commands.getAllValues()).allMatch(c -> !c.contains("rm"));
    }
}
