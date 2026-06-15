package com.skm.payments.application;

import jakarta.validation.constraints.Positive;

/** Request to refund {@code amount} (minor units) of a payment. */
public record RefundRequest(@Positive long amount) {}
