package com.skm.payments.application;

/** The idempotency key was reused with a different request body. Maps to HTTP 422. */
public class IdempotencyMismatchException extends RuntimeException {

  public IdempotencyMismatchException(String idempotencyKey) {
    super(
        "idempotency key '%s' was already used with a different request body"
            .formatted(idempotencyKey));
  }
}
