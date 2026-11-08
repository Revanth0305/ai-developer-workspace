package dev.workspace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:workspace-context-test",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class WorkspaceApplicationTests {

	@Test
	void contextLoads() {
	}

}
