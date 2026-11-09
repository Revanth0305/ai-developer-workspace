package dev.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class BoundedProcessTests {
    @TempDir Path cwd;
    String java() { return Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString(); }
    List<String> command(String mode) throws Exception {
        return List.of(java(),"-cp",Path.of(Child.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),Child.class.getName(),mode);
    }
    @Test void outputLimitDoesNotDeadlockAndReturnsRealExitStatus() throws Exception {
        var result=new BoundedProcess().run(command("output"),cwd,Duration.ofSeconds(8),()->false,s->{});
        assertThat(result.exitCode()).isEqualTo(7);assertThat(result.output()).hasSize(65536);
    }
    @Test void timeoutsAndCancellationStopOwnedChildren() {
        assertThatThrownBy(() -> new BoundedProcess().run(command("wait"),cwd,Duration.ofMillis(150),()->false,s->{})).isInstanceOf(TimeoutException.class);
        assertThatThrownBy(() -> new BoundedProcess().run(command("wait"),cwd,Duration.ofSeconds(5),()->true,s->{})).isInstanceOf(CancellationException.class);
    }
    public static class Child {
        public static void main(String[] args) throws Exception {
            if(args[0].equals("output")) { System.out.print("x".repeat(200000));System.exit(7); }
            Thread.sleep(30000);
        }
    }
}
