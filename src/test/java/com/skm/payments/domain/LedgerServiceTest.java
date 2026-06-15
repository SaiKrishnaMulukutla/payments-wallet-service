package com.skm.payments.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit test for the double-entry invariant — no database needed. */
class LedgerServiceTest {

    private final LedgerService ledger = new LedgerService(null, null, null);
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void acceptsBalancedTransaction() {
        assertThatCode(() -> ledger.assertBalanced(List.of(
                new LedgerService.Posting(a, Direction.DEBIT, 100),
                new LedgerService.Posting(b, Direction.CREDIT, 100)
        ))).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnbalancedTransaction() {
        assertThatThrownBy(() -> ledger.assertBalanced(List.of(
                new LedgerService.Posting(a, Direction.DEBIT, 100),
                new LedgerService.Posting(b, Direction.CREDIT, 90)
        ))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> ledger.assertBalanced(List.of(
                new LedgerService.Posting(a, Direction.DEBIT, 0),
                new LedgerService.Posting(b, Direction.CREDIT, 0)
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFewerThanTwoPostings() {
        assertThatThrownBy(() -> ledger.assertBalanced(List.of(
                new LedgerService.Posting(a, Direction.DEBIT, 100)
        ))).isInstanceOf(IllegalArgumentException.class);
    }
}
