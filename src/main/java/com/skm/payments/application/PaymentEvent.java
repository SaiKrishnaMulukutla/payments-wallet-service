package com.skm.payments.application;

import com.skm.payments.domain.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

/** The payload published when a payment reaches a terminal state. */
public record PaymentEvent(
    UUID eventId,
    UUID paymentId,
    String merchantId,
    PaymentStatus status,
    long amount,
    String currency,
    Instant occurredAt) {}
