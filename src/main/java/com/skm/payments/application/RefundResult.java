package com.skm.payments.application;

/** A refund response plus the HTTP status to return and whether it was an idempotent replay. */
public record RefundResult(RefundResponse response, int statusCode, boolean replayed) {}
