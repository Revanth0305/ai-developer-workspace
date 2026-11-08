package dev.workspace;

import tools.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Duration;

/** Injectable transport boundary; tests never need a real signed-in account. */
public interface PlanningClient extends AutoCloseable {
    void initialize() throws Exception;
    JsonNode request(String method, Object params) throws Exception;
    void prepare(Path cwd) throws Exception;
    String plan(String prompt, Path cwd, Duration timeout) throws Exception;
    String threadId();
    void interrupt();
    @Override void close();
}
