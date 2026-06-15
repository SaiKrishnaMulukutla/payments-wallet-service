package com.skm.payments.domain;

import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.repository.LedgerEntryRepository;
import com.skm.payments.repository.LedgerTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The ledger primitive: post a balanced, immutable double-entry transaction.
 *
 * <p>The {@link #assertBalanced} invariant (postings net to zero) is the heart of the
 * ledger and is fully implemented here; the database also enforces it via a deferred
 * constraint trigger (defense in depth). Balance maintenance under a pessimistic
 * {@code SELECT ... FOR UPDATE} with the non-negative guard is the M1 task — see the
 * TODO in {@link #post}.
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
     * Post a balanced transaction. Returns the new transaction id.
     * Runs in one DB transaction; the deferred trigger validates the net-zero
     * invariant again at COMMIT.
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

        for (Posting p : postings) {
            LedgerEntry entry = new LedgerEntry();
            entry.setTransactionId(txn.getId());
            entry.setAccountId(p.accountId());
            entry.setDirection(p.direction());
            entry.setAmount(p.amount());
            entry.setCreatedAt(Instant.now());
            entries.save(entry);

            // TODO (M1): balances.findByIdForUpdate(p.accountId()) ->
            //   apply signed delta, enforce non-negative, save (optimistic @Version).
        }

        return txn.getId();
    }

    /**
     * The double-entry invariant: a transaction needs >= 2 postings, each with a
     * positive amount, whose signed sum is zero (DEBIT = +amount, CREDIT = -amount).
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
