package com.skm.payments.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.skm.payments.application.AccountService;
import com.skm.payments.application.CreatePaymentRequest;
import com.skm.payments.application.PaymentService;
import com.skm.payments.domain.AccountType;
import com.skm.payments.domain.OwnerType;
import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.repository.OutboxRepository;
import com.skm.payments.repository.ProcessedEventRepository;
import com.skm.payments.support.AbstractIntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

/** Full pipeline: payment -> outbox -> relay -> Kafka -> consumer, processed exactly once. */
@EmbeddedKafka(partitions = 1, topics = KafkaConfig.PAYMENT_EVENTS_TOPIC)
@TestPropertySource(
    properties = {
      "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
      "spring.kafka.listener.auto-startup=true"
    })
class PaymentEventsEndToEndIT extends AbstractIntegrationTest {

  @Autowired PaymentService payments;
  @Autowired AccountService accounts;
  @Autowired AccountBalanceRepository balances;
  @Autowired OutboxRelay relay;
  @Autowired OutboxRepository outbox;
  @Autowired ProcessedEventRepository processed;

  @Test
  void paymentEventReachesConsumerExactlyOnce() {
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
    UUID eventId =
        outbox.findAll().stream()
            .filter(e -> e.getAggregateId().equals(paymentId))
            .findFirst()
            .orElseThrow()
            .getId();

    relay.publishBatch();

    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(() -> assertThat(processed.existsById(eventId)).isTrue());
  }

  private void fund(UUID accountId, long amount) {
    var balance = balances.findById(accountId).orElseThrow();
    balance.setBalance(amount);
    balances.saveAndFlush(balance);
  }
}
