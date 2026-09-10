package io.github.mohammedfaraj46.backend.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need a real database.
 *
 * <p>Starts one PostgreSQL 16 container — the same major version as production —
 * and lets {@link ServiceConnection} point Spring's datasource at it, so Flyway
 * applies the real migration and Hibernate validates the mapping against the
 * result. No test touches the local development database.
 *
 * <p>The container is started once in a static initialiser rather than with
 * {@code @Testcontainers}/{@code @Container}. That extension starts and stops the
 * container per test class, which would tear this shared instance down after the
 * first class and bring it back on a different port, invalidating the Spring
 * contexts cached against it. Started this way it lives for the whole JVM and
 * Testcontainers' Ryuk sidecar removes it when the run ends.
 */
public abstract class AbstractPostgresIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    /**
     * A fixed signing key for tests. In production this comes from the JWT_SECRET
     * environment variable and there is no fallback, so without this the context
     * would refuse to start.
     */
    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> "test-secret-that-is-long-enough-for-hs256");
    }
}
