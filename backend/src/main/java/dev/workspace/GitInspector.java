package dev.workspace;

import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

@Service
public class GitInspector {
    public record Status(boolean repository, String branch, String changes, String commits, String message) {}
    public Status inspect(Path directory) {
        try {
            if (!run(directory, "rev-parse", "--is-inside-work-tree").strip().equals("true")) {
                return new Status(false, "", "", "", "This folder is not a Git repository.");
            }
            String top = run(directory, "rev-parse", "--show-toplevel").strip();
            if (!Path.of(top).toRealPath().equals(directory.toRealPath())) {
                return new Status(false, "", "", "", "Select the repository root to inspect Git status.");
            }
            String branch = run(directory, "branch", "--show-current").strip();
            String changes = run(directory, "status", "--short", "--untracked-files=normal");
            String commits = run(directory, "log", "-5", "--format=%h %s");
            return new Status(true, branch.isBlank() ? "Detached HEAD" : branch, changes, commits.startsWith("fatal:") ? "" : commits, "Read-only Git inspection");
        } catch (Exception e) {
            return new Status(false, "", "", "", "Git inspection unavailable. Check that Git is installed and the folder is trusted.");
        }
    }

    private String run(Path directory, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "--no-optional-locks", "-c", "core.fsmonitor=false", "-C", directory.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> output = executor.submit(() -> {
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                try (InputStream stream = process.getInputStream()) {
                    int count;
                    while ((count = stream.read(buffer)) != -1) {
                        if (result.size() < 32768) result.write(buffer, 0, Math.min(count, 32768 - result.size()));
                    }
                }
                return result.toString(StandardCharsets.UTF_8);
            });
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Git timed out");
            }
            return output.get(2, TimeUnit.SECONDS);
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}
