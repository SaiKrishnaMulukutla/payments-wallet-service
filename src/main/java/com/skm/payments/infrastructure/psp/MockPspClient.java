package com.skm.payments.infrastructure.psp;

import com.skm.payments.domain.Payment;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Stand-in payment provider for local development: approves every authorization. */
@Component
public class MockPspClient implements PaymentProvider {

  @Override
  public AuthorizationResult authorize(Payment payment) {
    return AuthorizationResult.approved("psp_" + UUID.randomUUID());
  }
}
