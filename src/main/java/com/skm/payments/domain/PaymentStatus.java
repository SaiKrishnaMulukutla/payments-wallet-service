package com.skm.payments.domain;

/** Lifecycle of a payment. */
public enum PaymentStatus {
  CREATED,
  PROCESSING,
  AUTHORIZED,
  SUCCEEDED,
  PARTIALLY_REFUNDED,
  REFUNDED,
  FAILED,
  FUNDS_LOCKED
}
