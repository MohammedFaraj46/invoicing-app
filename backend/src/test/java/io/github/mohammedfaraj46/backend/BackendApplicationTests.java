package io.github.mohammedfaraj46.backend;

import io.github.mohammedfaraj46.backend.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the whole application context starts, Flyway migrates and
 * Hibernate validates its mapping against the migrated schema.
 */
@SpringBootTest
class BackendApplicationTests extends AbstractPostgresIntegrationTest {

	@Test
	void contextLoads() {
	}

}
