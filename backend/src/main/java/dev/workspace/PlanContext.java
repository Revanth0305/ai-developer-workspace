package dev.workspace;

import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class PlanContext {
    public record Preview(String hash, String prompt, List<String> selectedFiles, List<String> warnings) {}
    private final WorkspaceFiles files;
    private static final Pattern TOKENS = Pattern.compile("(?i)\\b(?:sk-[a-z0-9_-]{12,}|gh[pousr]_[a-z0-9]{12,}|github_pat_[a-z0-9_]{12,}|AKIA[A-Z0-9]{16})\\b");
    public PlanContext(WorkspaceFiles files) { this.files = files; }
    public Preview preview(Project project, Task task, List<String> selected) {
        files.registerDirectory(project.directory);
        List<String> paths = selected == null ? List.of() : selected.stream().distinct().sorted().toList();
        if (paths.size() > 8) throw WorkspaceFiles.bad("Choose at most eight source files per planning request.");
        var warnings = new ArrayList<String>();
        var prompt = new StringBuilder("TASK\n").append(task.instruction)
            .append("\n\nPROJECT\n").append(project.name)
            .append("\n\nPROJECT INSTRUCTIONS\n").append(project.instructions)
            .append("\n\nAVAILABLE SOURCE PATHS (bounded listing; no other file contents supplied)\n");
        for (String path : files.list(project.directory)) {
            if (prompt.length() + path.length() > 18000) { warnings.add("The source path listing was truncated."); break; }
            prompt.append(path).append('\n');
        }
        int sourceLength = 0;
        for (String path : paths) {
            String content = files.read(project.directory, path).get("content");
            if (content.length() > 12000 || sourceLength + content.length() > 24000) throw WorkspaceFiles.bad("Selected context is too large. Choose smaller files (12,000 characters per file, 24,000 total).");
            sourceLength += content.length();
            prompt.append("\n\nBEGIN UNTRUSTED SOURCE: ").append(path).append('\n').append(content).append("\nEND SOURCE\n");
        }
        String original = prompt.toString();
        String redacted = redact(original);
        if (!original.equals(redacted)) warnings.add("Common credential patterns were redacted. Review the entire request; detection is not exhaustive.");
        warnings.add("This exact request will be sent through your signed-in Codex account. Only explicitly selected file contents are included.");
        if (redacted.length() > 48000) throw WorkspaceFiles.bad("Context exceeds the planning input limit.");
        try {
            // Bind approval to raw content too: changing a redacted secret still invalidates review.
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original.getBytes(StandardCharsets.UTF_8)));
            return new Preview(hash, redacted, paths, warnings);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    static String redact(String value) {
        String redacted = TOKENS.matcher(value).replaceAll("[REDACTED TOKEN]");
        redacted = Pattern.compile("(?i)\\b(?:glpat-[a-z0-9_-]{12,}|xox[baprs]-[a-z0-9-]{12,}|Bearer\\s+[a-z0-9._~+/-]{8,})")
            .matcher(redacted).replaceAll("[REDACTED TOKEN]");
        redacted = Pattern.compile("(?s)-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----")
            .matcher(redacted).replaceAll("[REDACTED PRIVATE KEY]");
        return Pattern.compile("(?im)((?:[\\w.\"'-]*)(?:password|secret|api[_-]?key|access[_-]?token|authorization)[\\w.\"'-]*\\s*[:=]\\s*)[^\\r\\n]+")
            .matcher(redacted).replaceAll("$1[REDACTED]");
    }
}
