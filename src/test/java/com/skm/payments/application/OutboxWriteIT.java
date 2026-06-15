package com.skm.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.skm.payments.domain.AccountType;
import com.skm.payments.domain.OutboxEvent;
import com.skm.payments.domain.OwnerType;
import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.repository.OutboxRepository;
import com.skm.payments.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A successful payment writes an (unpublished) outbox event atomically with the state change. */
class OutboxWriteIT extends AbstractIntegrationTest {

  @Autowired PaymentService payments;
  @Autowired AccountService accounts;
  @Autowired AccountBalanceRepository balances;
  @Autowired OutboxRepository outbox;

  @Test
  void successfulPaymentWritesUnpublishedSucceededEvent() {
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
    fund(payer, 100);

    var paymentId =
        payments
            .create(
                UUID.randomUUID().toString(),
                new CreatePaymentRequest("m1", payer, payee, 30, "INR"))
            .response()
            .id();

    OutboxEvent event = eventFor(paymentId);
    assertThat(event.getEventType()).isEqualTo(PaymentEventTypes.SUCCEEDED);
    assertThat(event.getPublishedAt()).isNull();
    assertThat(event.getPayload()).contains(paymentId.toString());
  }

  private OutboxEvent eventFor(UUID paymentId) {
    return outbox.findAll().stream()
        .filter(e -> e.getAggregateId().equals(paymentId))
        .findFirst()
        .orElseThrow();
  }

  private void fund(UUID accountId, long amount) {
    var balance = balances.findById(accountId).orElseThrow();
    balance.setBalance(amount);
    balances.saveAndFlush(balance);
  }
}
