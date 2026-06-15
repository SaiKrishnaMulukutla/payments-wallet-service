package com.skm.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.skm.payments.domain.AccountType;
import com.skm.payments.domain.OwnerType;
import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.repository.PaymentRepository;
import com.skm.payments.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Headline idempotency test: 50 concurrent requests with the same idempotency key must create
 * exactly one payment and debit the payer exactly once. Each request either creates/replays the one
 * payment or is rejected as in-progress; none double-charges.
 */
class PaymentServiceConcurrencyIT extends AbstractIntegrationTest {

  @Autowired PaymentService payments;
  @Autowired AccountService accounts;
  @Autowired AccountBalanceRepository balances;
  @Autowired PaymentRepository paymentRepo;

  @Test
  void fiftyIdenticalRequestsCreateExactlyOnePayment() throws Exception {
    var payer =
        accounts
            .createAccount(
                OwnerType.USER, "payer-" + UUID.randomUUID(), AccountType.USER_WALLET, "INR")
            .getId();
    var payee =
        accounts
            .createAccount(
                OwnerType.MERCHANT,
                "payee-" + UUID.randomUUID(),
                AccountType.MERCHANT_PAYABLE,
                "INR")
            .getId();
    fund(payer, 1000);

    String key = UUID.randomUUID().toString();
    var request = new CreatePaymentRequest("m-" + UUID.randomUUID(), payer, payee, 100, "INR");

    int attempts = 50;
    ExecutorService pool = Executors.newFixedThreadPool(16);
    CountDownLatch startGate = new CountDownLatch(1);
    AtomicInteger createdOrReplayed = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();
    AtomicInteger unexpected = new AtomicInteger();
    List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < attempts; i++) {
      futures.add(
          pool.submit(
              () -> {
                try {
                  startGate.await();
                  payments.create(key, request);
                  createdOrReplayed.incrementAndGet();
                } catch (IdempotencyConflictException inProgress) {
                  conflicts.incrementAndGet();
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
    assertThat(createdOrReplayed.get() + conflicts.get()).isEqualTo(attempts);
    assertThat(createdOrReplayed.get()).as("at least one succeeded").isGreaterThanOrEqualTo(1);
    assertThat(paymentRepo.countByPayerAccount(payer)).as("exactly one payment").isEqualTo(1);
    assertThat(balanceOf(payer)).as("payer debited exactly once").isEqualTo(900);
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
