package dev.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

class GitInspectorTests {
    @TempDir Path directory;
    @Test void inspectsUnbornRepositoryWithoutChangingFilesAndRejectsNestedFolder() throws Exception {
        Process process = new ProcessBuilder("git", "init", "--initial-branch=main", directory.toString()).redirectErrorStream(true).start();
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        Path source = directory.resolve("README.md");
        Files.writeString(source, "# Source must stay unchanged");
        var inspector = new GitInspector();
        var result = inspector.inspect(directory);
        assertThat(result.repository()).isTrue();
        assertThat(result.branch()).isEqualTo("main");
        assertThat(result.changes()).contains("README.md");
        assertThat(result.commits()).isEmpty();
        assertThat(Files.readString(source)).isEqualTo("# Source must stay unchanged");
        Path child = Files.createDirectory(directory.resolve("child"));
        assertThat(inspector.inspect(child).repository()).isFalse();
        assertThat(inspector.inspect(child).message()).contains("repository root");
    }
    @Test void nonRepositoryIsReportedHonestly() {
        assertThat(new GitInspector().inspect(directory).repository()).isFalse();
    }
}
