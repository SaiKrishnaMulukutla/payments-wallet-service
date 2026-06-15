package com.skm.payments.application;

/** A payment response plus the HTTP status to return and whether it was an idempotent replay. */
public record PaymentResult(PaymentResponse response, int statusCode, boolean replayed) {}
