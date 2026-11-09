package dev.workspace;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

@Service
public class ChangeDocument {
    public record Change(String path,String operation,String content) {}
    public record Proposal(String summary,List<Change> changes) {}
    private final WorkspaceFiles files;
    private final JsonMapper json=JsonMapper.builder().build();
    public ChangeDocument(WorkspaceFiles files) { this.files=files; }
    public static Map<String,Object> schema() {
        Map<String,Object> string=Map.of("type","string");
        Map<String,Object> change=Map.of("type","object","additionalProperties",false,
            "properties",Map.of("path",string,"operation",Map.of("type","string","enum",List.of("write","delete")),"content",string),
            "required",List.of("path","operation","content"));
        return Map.of("type","object","additionalProperties",false,"properties",Map.of("summary",string,"changes",Map.of("type","array","items",change)),"required",List.of("summary","changes"));
    }
    public Proposal validate(String response,Set<String> allowed,Set<String> existing) {
        if(response==null || response.length()>64000) throw WorkspaceFiles.bad("Change proposal exceeds the output limit.");
        JsonNode body=json.readTree(response);
        if(!body.isObject() || !body.path("summary").isString() || body.path("summary").asText().isBlank() || body.path("summary").asText().length()>2000
                || !body.path("changes").isArray() || body.path("changes").isEmpty() || body.path("changes").size()>8) throw WorkspaceFiles.bad("Invalid structured change proposal.");
        List<Change> changes=new ArrayList<>(); Set<String> seen=new HashSet<>(); int total=0;
        for(JsonNode node:body.path("changes")) {
            if(!node.path("path").isString() || !node.path("operation").isString() || !node.path("content").isString()) throw WorkspaceFiles.bad("Invalid file change.");
            String path=node.path("path").asText().replace('\\','/');
            String operation=node.path("operation").asText(),content=node.path("content").asText();
            if(!files.sourcePath(path) || !allowed.contains(path) || !seen.add(path.toLowerCase(Locale.ROOT))
                    || !Set.of("write","delete").contains(operation) || content.indexOf('\0')>=0 || content.length()>32000)
                throw WorkspaceFiles.bad("The proposal contains an unauthorized or oversized file change.");
            if(operation.equals("delete") && (!existing.contains(path) || !content.isEmpty())) throw WorkspaceFiles.bad("Only existing authorized files can be deleted.");
            if(!PlanContext.redact(content).equals(content)) throw WorkspaceFiles.bad("The proposal contains credential-like content. It was not staged.");
            total+=content.length(); if(total>48000) throw WorkspaceFiles.bad("The proposed changes exceed the size limit.");
            changes.add(new Change(path,operation,content));
        }
        return new Proposal(PlanContext.redact(body.path("summary").asText()),List.copyOf(changes));
    }
}
