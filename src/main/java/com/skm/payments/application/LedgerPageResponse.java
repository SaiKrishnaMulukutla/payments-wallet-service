package com.skm.payments.application;

import java.util.List;
import java.util.UUID;

/** A bounded page of an account's most-recent postings (newest first). */
public record LedgerPageResponse(UUID accountId, int count, List<LedgerEntryResponse> entries) {}
