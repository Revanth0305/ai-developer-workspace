package dev.workspace;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@Service
public class WorkspaceFiles {
    private final Path platformRoot;
    private static final Set<String> EXCLUDED = Set.of("node_modules", "build", "dist", "target", "vendor", "coverage", "__pycache__");
    private static final Set<String> EXTENSIONS = Set.of("java", "ts", "tsx", "js", "jsx", "json", "md", "css", "html", "xml", "gradle", "yml", "yaml", "properties", "txt", "py", "sql");

    public WorkspaceFiles(@Value("${workspace.platform-root}") String platformRoot) throws IOException {
        this.platformRoot = Path.of(platformRoot).toRealPath();
    }

    public Path registerDirectory(String value) {
        try {
            Path given = Path.of(value);
            if (!given.isAbsolute()) throw bad("Use an absolute path to an existing project folder.");
            Path real = given.toRealPath();
            if (!Files.isDirectory(real)) throw bad("The selected path is not a directory.");
            if (real.startsWith(platformRoot) || platformRoot.startsWith(real)) {
                throw bad("Choose a project outside the platform repository and its parent directories.");
            }
            return real;
        } catch (InvalidPathException | IOException e) {
            throw bad("Project folder not found or inaccessible.");
        }
    }

    public List<String> list(String directory) {
        Path root = registerDirectory(directory);
        List<String> files = new ArrayList<>();
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 12, new SimpleFileVisitor<>() {
                int visited;
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (++visited > 5000 || files.size() >= 250) return FileVisitResult.TERMINATE;
                    if (!dir.equals(root) && blocked(dir.getFileName().toString())) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (++visited > 5000 || files.size() >= 250) return FileVisitResult.TERMINATE;
                    if (attrs.isRegularFile() && !Files.isSymbolicLink(file) && allowed(root.relativize(file))) {
                        files.add(root.relativize(file).toString().replace('\\', '/'));
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path file, IOException e) { return FileVisitResult.CONTINUE; }
            });
            files.sort(String::compareToIgnoreCase);
            return files;
        } catch (IOException e) { throw bad("Unable to inspect this project folder."); }
    }

    public Map<String, String> read(String directory, String relative) {
        try {
            Path root = registerDirectory(directory);
            Path requested = Path.of(relative);
            if (requested.isAbsolute() || !allowed(requested)) throw bad("That file is excluded from source inspection.");
            Path normalized = root.resolve(requested).normalize();
            if (!normalized.startsWith(root)) throw bad("File must stay inside the selected project.");
            Path real = normalized.toRealPath();
            if (!real.startsWith(root) || !allowed(root.relativize(real)) || !Files.isRegularFile(real)) throw bad("File must stay inside the selected project.");
            if (Files.size(real) > 64 * 1024) throw bad("Preview is limited to text files smaller than 64 KB.");
            String content = Files.readString(real, StandardCharsets.UTF_8);
            if (content.indexOf('\0') >= 0) throw bad("Binary files cannot be previewed.");
            return Map.of("path", relative, "content", content);
        } catch (InvalidPathException | IOException e) { throw bad("Unable to read this source file."); }
    }

    private boolean blocked(String name) { return name.startsWith(".") || EXCLUDED.contains(name.toLowerCase(Locale.ROOT)); }
    private boolean allowed(Path relative) {
        for (Path part : relative) if (blocked(part.toString()) || part.toString().equals("..")) return false;
        String name = relative.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.contains("secret") || name.contains("credential") || name.contains("private") || name.equals("application-local.properties")) return false;
        int dot = name.lastIndexOf('.');
        return name.equals("dockerfile") || (dot >= 0 && EXTENSIONS.contains(name.substring(dot + 1)));
    }

    static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
