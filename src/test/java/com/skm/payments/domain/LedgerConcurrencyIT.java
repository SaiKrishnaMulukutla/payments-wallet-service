package com.skm.payments.domain;

import com.skm.payments.application.AccountService;
import com.skm.payments.domain.LedgerService.Posting;
import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The headline correctness test: a wallet funded for 50 units, hit by 100 concurrent 1-unit
 * debits, must end with exactly 50 successes, 50 insufficient-funds rejections, a final balance
 * of 0, and must never go negative. Proves SELECT ... FOR UPDATE + the non-negative guard
 * prevent oversell.
 */
class LedgerConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    LedgerService ledger;
    @Autowired
    AccountService accounts;
    @Autowired
    AccountBalanceRepository balances;

    @Test
    void parallelDebitsNeverOversell() throws Exception {
        var wallet = accounts.createAccount("USER", "w-" + UUID.randomUUID(), "USER_WALLET", "INR");
        var sink = accounts.createAccount("SYSTEM", "sink-" + UUID.randomUUID(), "FEE_INCOME", "INR");
        fund(wallet.getId(), 50);

        int attempts = 100;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                try {
                    startGate.await();
                    ledger.post("PAYMENT", UUID.randomUUID(), List.of(
                            new Posting(wallet.getId(), Direction.DEBIT, 1),
                            new Posting(sink.getId(), Direction.CREDIT, 1)
                    ));
                    succeeded.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    insufficient.incrementAndGet();
                } catch (Exception e) {
                    unexpected.incrementAndGet();
                }
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(unexpected.get()).as("unexpected errors").isZero();
        assertThat(succeeded.get()).as("successful debits").isEqualTo(50);
        assertThat(insufficient.get()).as("insufficient-funds rejections").isEqualTo(50);
        assertThat(balanceOf(wallet.getId())).as("wallet never oversold").isEqualTo(0);
        assertThat(balanceOf(sink.getId())).isEqualTo(50);
    }

    private void fund(UUID accountId, long amount) {
        var balance = balances.findById(accountId).orElseThrow();
        balance.setBalance(amount);
        balances.saveAndFlush(balance);
    }

    private long balanceOf(UUID accountId) {
        return balances.findById(accountId).orElseThrow().getBalance();
    }
}
