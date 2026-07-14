package com.skm.payments.application;

import com.skm.payments.domain.Direction;
import java.time.Instant;
import java.util.UUID;

/** A single append-only posting on an account. */
public record LedgerEntryResponse(
    UUID transactionId, Direction direction, long amount, Instant createdAt) {}
