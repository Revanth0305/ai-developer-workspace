package dev.workspace;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Path;

@Component
public class PlanningClientFactory {
    public PlanningClient create(String executable, Path cwd) throws IOException {
        return new CodexConnection(executable, cwd);
    }
}
