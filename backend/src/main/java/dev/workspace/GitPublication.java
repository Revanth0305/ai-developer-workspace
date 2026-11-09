package dev.workspace;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="workspace_git_publications")
public class GitPublication {
    @Id public UUID id;
    public UUID projectId;
    public String status="PREPARED";
    public String account="", repository="", branch="", baseCommit="", treeId="", commitId="";
    public String authorName="", authorEmail="";
    @Column(length=500) public String message="";
    public String reviewHash="", pushHash="";
    @Lob public String diff="";
    @Column(length=1000) public String error="";
    public Instant updatedAt=Instant.now();
    protected GitPublication() {}
    GitPublication(UUID id) {this.id=id;}
    public String remoteUrl() {return "https://github.com/"+account+"/"+repository+".git";}
}
