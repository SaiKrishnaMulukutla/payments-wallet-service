package com.skm.payments;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full Spring context against a real Postgres (via Testcontainers) and
 * runs Flyway. Requires a running Docker daemon. Redis/Kafka connection factories
 * are lazy, so the context loads with only Postgres present.
 */
@SpringBootTest
@Testcontainers
class PaymentsApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void contextLoads() {
        // Passes if the context starts and Flyway migrations apply cleanly.
    }
}
