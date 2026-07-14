package com.skm.payments.application;

import com.skm.payments.domain.Account;
import com.skm.payments.domain.LedgerEntry;
import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.repository.AccountRepository;
import com.skm.payments.repository.LedgerEntryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only queries over accounts and their postings. */
@Service
public class AccountQueryService {

  private static final int DEFAULT_LEDGER_LIMIT = 20;
  private static final int MAX_LEDGER_LIMIT = 100;

  private final AccountRepository accounts;
  private final AccountBalanceRepository balances;
  private final LedgerEntryRepository entries;

  public AccountQueryService(
      AccountRepository accounts,
      AccountBalanceRepository balances,
      LedgerEntryRepository entries) {
    this.accounts = accounts;
    this.balances = balances;
    this.entries = entries;
  }

  /** Current materialized balance for an account. 404 if the account does not exist. */
  @Transactional(readOnly = true)
  public BalanceResponse getBalance(UUID accountId) {
    Account account =
        accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    return balances
        .findById(accountId)
        .map(
            b ->
                new BalanceResponse(
                    accountId, b.getBalance(), account.getCurrency(), b.getUpdatedAt()))
        .orElseGet(
            () ->
                new BalanceResponse(accountId, 0L, account.getCurrency(), account.getCreatedAt()));
  }

  /** A bounded page of the account's most-recent postings (newest first). */
  @Transactional(readOnly = true)
  public LedgerPageResponse getLedger(UUID accountId, Integer limit) {
    if (!accounts.existsById(accountId)) {
      throw new AccountNotFoundException(accountId);
    }
    List<LedgerEntryResponse> page =
        entries
            .findByAccountIdOrderByCreatedAtDesc(accountId, PageRequest.of(0, clamp(limit)))
            .stream()
            .map(this::toResponse)
            .toList();
    return new LedgerPageResponse(accountId, page.size(), page);
  }

  private LedgerEntryResponse toResponse(LedgerEntry e) {
    return new LedgerEntryResponse(
        e.getTransactionId(), e.getDirection(), e.getAmount(), e.getCreatedAt());
  }

  private int clamp(Integer limit) {
    if (limit == null || limit <= 0) {
      return DEFAULT_LEDGER_LIMIT;
    }
    return Math.min(limit, MAX_LEDGER_LIMIT);
  }
}
