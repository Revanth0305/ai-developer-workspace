package dev.workspace;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

/** Accept structured proposals only; model text is rendered as plain text by the dashboard. */
@Service
public class PlanDocument {
    private final WorkspaceFiles files;
    private final JsonMapper json = JsonMapper.builder().build();
    public PlanDocument(WorkspaceFiles files) { this.files = files; }

    public static Map<String, Object> schema() {
        Map<String, Object> string = Map.of("type", "string");
        Map<String, Object> change = Map.of("type", "object", "additionalProperties", false,
            "properties", Map.of("path", string, "change", string), "required", List.of("path", "change"));
        return Map.of("type", "object", "additionalProperties", false,
            "properties", Map.of("goal", string, "changes", Map.of("type", "array", "items", change),
                "verification", Map.of("type", "array", "items", string), "openQuestions", Map.of("type", "array", "items", string)),
            "required", List.of("goal", "changes", "verification", "openQuestions"));
    }

    public String validateAndRender(Project project, String response) {
        if (response == null || response.length() > 64000) throw WorkspaceFiles.bad("Plan exceeds the output limit.");
        JsonNode plan = json.readTree(response);
        if (!plan.isObject() || !plan.path("changes").isArray() || plan.path("changes").size() > 40)
            throw WorkspaceFiles.bad("Invalid structured plan.");
        StringBuilder result = new StringBuilder("## Goal\n\n").append(text(plan.path("goal"))).append("\n\n## Proposed changes\n\n");
        Set<String> paths = new HashSet<>();
        for (JsonNode change : plan.path("changes")) {
            String path = files.proposedPath(project.directory, text(change.path("path")));
            if (!paths.add(path)) throw WorkspaceFiles.bad("Plan contains duplicate source paths.");
            result.append("- ").append(path).append(": ").append(text(change.path("change"))).append('\n');
        }
        if (paths.isEmpty()) result.append("No file changes proposed; review the open questions.\n");
        appendList(result, "Verification", plan.path("verification"));
        appendList(result, "Open questions", plan.path("openQuestions"));
        result.append("\nPlanning only. No files were edited and no proposed checks were run.\n");
        String rendered = PlanContext.redact(result.toString());
        if (rendered.length() > 64000) throw WorkspaceFiles.bad("Plan exceeds the output limit.");
        return rendered;
    }

    private static String text(JsonNode node) {
        if (!node.isString() || node.asText().isBlank() || node.asText().length() > 12000)
            throw WorkspaceFiles.bad("Invalid plan text.");
        return node.asText().strip();
    }
    private static void appendList(StringBuilder result, String title, JsonNode list) {
        if (!list.isArray() || list.size() > 40) throw WorkspaceFiles.bad("Invalid plan list.");
        result.append("\n## ").append(title).append("\n\n");
        if (list.isEmpty()) result.append("None.\n");
        for (JsonNode item : list) result.append("- ").append(text(item)).append('\n');
    }
}
