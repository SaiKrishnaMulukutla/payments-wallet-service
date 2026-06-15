package com.skm.payments.application;

/** Webhook event types the PSP sends us. */
public final class PspWebhookTypes {

  public static final String PAYMENT_CAPTURED = "payment.captured";
  public static final String PAYMENT_FAILED = "payment.failed";

  private PspWebhookTypes() {}
}
