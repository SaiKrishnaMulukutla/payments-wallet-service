package com.skm.payments.application;

/** A request with the same idempotency key is still in progress. Maps to HTTP 409. */
public class IdempotencyConflictException extends RuntimeException {

  public IdempotencyConflictException(String idempotencyKey) {
    super("a request with idempotency key '%s' is already in progress".formatted(idempotencyKey));
  }
}
