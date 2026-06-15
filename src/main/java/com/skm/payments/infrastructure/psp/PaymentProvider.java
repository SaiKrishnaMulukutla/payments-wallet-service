package com.skm.payments.infrastructure.psp;

import com.skm.payments.domain.Payment;

/** Abstraction over an external payment provider, so the saga is provider-agnostic. */
public interface PaymentProvider {

  AuthorizationResult authorize(Payment payment);
}
