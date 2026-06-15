package com.skm.payments.application;

import java.util.List;
import java.util.UUID;

/** Outcome of a reconciliation run: ledger integrity plus how many stale holds were expired. */
public record ReconciliationReport(long ledgerNet, List<UUID> driftedAccounts, int expiredHolds) {

  public boolean ledgerBalanced() {
    return ledgerNet == 0 && driftedAccounts.isEmpty();
  }
}
