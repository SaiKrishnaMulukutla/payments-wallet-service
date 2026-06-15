package com.skm.payments.repository;

import com.skm.payments.domain.LedgerEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

  List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

  /** Net of all postings (debits minus credits) — must be zero in a balanced ledger. */
  @Query(
      value =
          "SELECT COALESCE(SUM(CASE direction WHEN 'DEBIT' THEN amount ELSE -amount END), 0)"
              + " FROM ledger_entries",
      nativeQuery = true)
  long signedNet();

  /**
   * Accounts whose materialized balance disagrees with the sum of their postings (credit - debit).
   */
  @Query(
      value =
          "SELECT b.account_id FROM account_balances b"
              + " LEFT JOIN (SELECT account_id,"
              + "   COALESCE(SUM(CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END), 0) AS computed"
              + "   FROM ledger_entries GROUP BY account_id) e ON e.account_id = b.account_id"
              + " WHERE b.balance <> COALESCE(e.computed, 0)",
      nativeQuery = true)
  List<UUID> findDriftedAccounts();
}
