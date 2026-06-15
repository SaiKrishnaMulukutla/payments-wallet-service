package com.skm.payments.infrastructure.psp;

import com.skm.payments.domain.Payment;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Stand-in payment provider for local development: authorizes and captures every payment. */
@Component
public class MockPspClient implements PaymentProvider {

  @Override
  public AuthorizationResult authorize(Payment payment) {
    return AuthorizationResult.captured("psp_" + UUID.randomUUID());
  }
}
