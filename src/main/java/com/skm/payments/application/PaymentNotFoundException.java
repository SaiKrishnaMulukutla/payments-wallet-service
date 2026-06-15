package com.skm.payments.application;

import java.util.UUID;

/** No payment exists for the given id. Maps to HTTP 404. */
public class PaymentNotFoundException extends RuntimeException {

  public PaymentNotFoundException(UUID id) {
    super("payment not found: " + id);
  }
}
