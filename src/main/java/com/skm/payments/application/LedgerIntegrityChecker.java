package com.skm.payments.application;

import com.skm.payments.repository.LedgerEntryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Verifies the ledger's invariants: debits equal credits, and balances match their postings. */
@Component
public class LedgerIntegrityChecker {

  private final LedgerEntryRepository entries;

  public LedgerIntegrityChecker(LedgerEntryRepository entries) {
    this.entries = entries;
  }

  @Transactional(readOnly = true)
  public Result check() {
    return new Result(entries.signedNet(), entries.findDriftedAccounts());
  }

  /** {@code ledgerNet} must be 0 and {@code driftedAccounts} empty for an intact ledger. */
  public record Result(long ledgerNet, List<UUID> driftedAccounts) {
    public boolean balanced() {
      return ledgerNet == 0 && driftedAccounts.isEmpty();
    }
  }
}
