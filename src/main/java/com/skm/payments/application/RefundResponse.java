package com.skm.payments.application;

import com.skm.payments.domain.RefundStatus;
import java.time.Instant;
import java.util.UUID;

/** Public view of a refund. */
public record RefundResponse(
    UUID id, UUID paymentId, long amount, RefundStatus status, Instant createdAt) {}
