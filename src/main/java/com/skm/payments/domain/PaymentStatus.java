package com.skm.payments.domain;

/** Lifecycle of a payment. */
public enum PaymentStatus {
  CREATED,
  PROCESSING,
  AUTHORIZED,
  SUCCEEDED,
  FAILED,
  FUNDS_LOCKED
}
