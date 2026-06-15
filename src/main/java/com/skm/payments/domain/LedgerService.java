package com.skm.payments.domain;

import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.repository.LedgerEntryRepository;
import com.skm.payments.repository.LedgerTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The ledger primitive: post a balanced, immutable double-entry transaction and
 * maintain account balances safely under concurrency.
 *
 * <p>Balance convention: a CREDIT increases an account's balance, a DEBIT decreases it.
 * Balance rows are locked with {@code SELECT ... FOR UPDATE} in ascending account-id order
 * (deterministic lock ordering avoids deadlocks), the signed delta is applied, and the
 * non-negative invariant is enforced before the postings are written — all in one
 * transaction. The database also enforces the net-zero invariant via a deferred trigger.
 */
@Service
public class LedgerService {

    private final LedgerTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final AccountBalanceRepository balances;

    public LedgerService(LedgerTransactionRepository transactions,
                         LedgerEntryRepository entries,
                         AccountBalanceRepository balances) {
        this.transactions = transactions;
        this.entries = entries;
        this.balances = balances;
    }

    /** A single posting within a transaction. {@code amount} is positive minor units. */
    public record Posting(UUID accountId, Direction direction, long amount) {
    }

    /**
     * Post a balanced transaction; returns the new transaction id. Runs in one DB
     * transaction. Throws {@link InsufficientFundsException} if any debit would make a
     * balance negative (the whole transaction then rolls back).
     */
    @Transactional
    public UUID post(String type, UUID referenceId, List<Posting> postings) {
        assertBalanced(postings);

        LedgerTransaction txn = new LedgerTransaction();
        txn.setId(UUID.randomUUID());
        txn.setType(type);
        txn.setReferenceId(referenceId);
        txn.setCreatedAt(Instant.now());
        transactions.save(txn);

        // Lock balance rows in a deterministic order (ascending account id) to prevent deadlocks.
        List<Posting> lockOrdered = postings.stream()
                .sorted(Comparator.comparing(Posting::accountId))
                .toList();

        for (Posting p : lockOrdered) {
            AccountBalance balance = balances.findByIdForUpdate(p.accountId())
                    .orElseThrow(() -> new IllegalStateException(
                            "no balance row for account " + p.accountId()));

            long delta = (p.direction() == Direction.CREDIT) ? p.amount() : -p.amount();
            long updated = balance.getBalance() + delta;
            if (updated < 0) {
                throw new InsufficientFundsException(p.accountId(), balance.getBalance(), p.amount());
            }
            balance.setBalance(updated);
            balance.setUpdatedAt(Instant.now());
            balances.save(balance);
        }

        for (Posting p : postings) {
            LedgerEntry entry = new LedgerEntry();
            entry.setTransactionId(txn.getId());
            entry.setAccountId(p.accountId());
            entry.setDirection(p.direction());
            entry.setAmount(p.amount());
            entry.setCreatedAt(Instant.now());
            entries.save(entry);
        }

        return txn.getId();
    }

    /**
     * The double-entry invariant: at least 2 postings, each with a positive amount, whose
     * signed sum is zero (DEBIT = +amount, CREDIT = -amount, so total debits = total credits).
     */
    void assertBalanced(List<Posting> postings) {
        if (postings == null || postings.size() < 2) {
            throw new IllegalArgumentException("a ledger transaction needs at least 2 postings");
        }
        long net = 0;
        for (Posting p : postings) {
            if (p.amount() <= 0) {
                throw new IllegalArgumentException("posting amount must be positive: " + p.amount());
            }
            net += (p.direction() == Direction.DEBIT) ? p.amount() : -p.amount();
        }
        if (net != 0) {
            throw new IllegalStateException("unbalanced ledger transaction: net = " + net);
        }
    }
}
