package com.skm.payments.application;

import java.time.Instant;
import java.util.UUID;

/** Materialized balance for an account, in minor units. */
public record BalanceResponse(UUID accountId, long balance, String currency, Instant updatedAt) {}
