package dev.workspace;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface GitPublicationRepository extends JpaRepository<GitPublication,UUID> {}
