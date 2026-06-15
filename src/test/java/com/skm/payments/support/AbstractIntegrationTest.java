package com.skm.payments.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests. Two modes:
 *
 * <ul>
 *   <li><b>Default</b> — spins a singleton Postgres via Testcontainers (needs a reachable Docker
 *       daemon; the normal way to run on a developer host).
 *   <li><b>External DB</b> — if {@code EXTERNAL_DB_URL} is set, connects to that Postgres instead
 *       of starting one. Used when the build itself runs inside a container on the same Docker
 *       network as a sibling Postgres (e.g. on Rancher Desktop, where mounting the Docker socket
 *       into the build container isn't reliable).
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  private static final String EXTERNAL_DB_URL = System.getenv("EXTERNAL_DB_URL");
  private static final boolean USE_EXTERNAL_DB =
      EXTERNAL_DB_URL != null && !EXTERNAL_DB_URL.isBlank();

  private static PostgreSQLContainer<?> postgres;

  static {
    if (!USE_EXTERNAL_DB) {
      postgres = new PostgreSQLContainer<>("postgres:16-alpine");
      postgres.start();
    }
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    if (USE_EXTERNAL_DB) {
      registry.add("spring.datasource.url", () -> EXTERNAL_DB_URL);
      registry.add(
          "spring.datasource.username",
          () -> System.getenv().getOrDefault("EXTERNAL_DB_USER", "payments"));
      registry.add(
          "spring.datasource.password",
          () -> System.getenv().getOrDefault("EXTERNAL_DB_PASSWORD", "payments"));
    } else {
      registry.add("spring.datasource.url", postgres::getJdbcUrl);
      registry.add("spring.datasource.username", postgres::getUsername);
      registry.add("spring.datasource.password", postgres::getPassword);
    }
    // Keep the per-context pool small: several test contexts (distinct configs) each hold a pool,
    // and their sum must stay under Postgres's connection limit. The concurrency tests just queue.
    registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
  }
}
