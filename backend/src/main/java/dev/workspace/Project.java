package dev.workspace;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_projects")
public class Project {
    @Id public UUID id = UUID.randomUUID();
    @Column(nullable = false) public String name;
    @Column(nullable = false, unique = true, length = 2048) public String directory;
    @Column(length = 8000) public String instructions = "";
    @Column(nullable = false) public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();
    protected Project() {}
    Project(String name, String directory, String instructions) {
        this.name = name;
        this.directory = directory;
        this.instructions = instructions;
    }
}
