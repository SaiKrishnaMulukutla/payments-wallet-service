package com.skm.payments;

import com.skm.payments.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/** Boots the full context against a real Postgres and runs Flyway migrations. */
class PaymentsApplicationTests extends AbstractIntegrationTest {

  @Test
  void contextLoads() {
    // Passes if the context starts and the V1 migration applies cleanly.
  }
}
