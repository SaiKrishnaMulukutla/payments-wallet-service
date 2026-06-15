package com.skm.payments.application;

/** Event type names published for payments (also used as the Kafka message's logical type). */
public final class PaymentEventTypes {

  public static final String SUCCEEDED = "payment.succeeded";
  public static final String FAILED = "payment.failed";

  private PaymentEventTypes() {}
}
