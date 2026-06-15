package com.skm.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skm.payments.domain.AccountType;
import com.skm.payments.domain.OwnerType;
import com.skm.payments.domain.PaymentStatus;
import com.skm.payments.infrastructure.psp.AuthorizationResult;
import com.skm.payments.infrastructure.psp.PaymentProvider;
import com.skm.payments.infrastructure.psp.WebhookSignatureVerifier;
import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.repository.OutboxRepository;
import com.skm.payments.repository.PaymentRepository;
import com.skm.payments.repository.WebhookEventRepository;
import com.skm.payments.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Async capture: a PENDING authorization is settled (or failed) by a signed, idempotent webhook.
 */
@Import(WebhookCaptureIT.PendingPspConfig.class)
class WebhookCaptureIT extends AbstractIntegrationTest {

  @Autowired PaymentService payments;
  @Autowired WebhookService webhooks;
  @Autowired AccountService accounts;
  @Autowired AccountBalanceRepository balances;
  @Autowired PaymentRepository paymentRepo;
  @Autowired OutboxRepository outbox;
  @Autowired WebhookEventRepository webhookEvents;
  @Autowired WebhookSignatureVerifier verifier;
  @Autowired ObjectMapper json;

  @TestConfiguration
  static class PendingPspConfig {
    @Bean
    @Primary
    PaymentProvider pendingPsp() {
      return payment -> AuthorizationResult.pending("psp_" + UUID.randomUUID());
    }
  }

  @Test
  void capturedWebhookSettlesAuthorizedPayment() throws Exception {
    Ctx ctx = authorizedPayment(30);
    assertThat(paymentRepo.findById(ctx.paymentId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(balanceOf(ctx.payee())).isZero();

    deliver(UUID.randomUUID().toString(), ctx.reference(), PspWebhookTypes.PAYMENT_CAPTURED);

    var captured = paymentRepo.findById(ctx.paymentId()).orElseThrow();
    assertThat(captured.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    assertThat(balanceOf(ctx.payee())).isEqualTo(30);
    assertThat(
            outbox.findAll().stream()
                .anyMatch(
                    e ->
                        e.getAggregateId().equals(ctx.paymentId())
                            && e.getEventType().equals(PaymentEventTypes.SUCCEEDED)))
        .isTrue();
  }

  @Test
  void failedWebhookReversesAuthorizedPayment() throws Exception {
    Ctx ctx = authorizedPayment(40);

    deliver(UUID.randomUUID().toString(), ctx.reference(), PspWebhookTypes.PAYMENT_FAILED);

    assertThat(paymentRepo.findById(ctx.paymentId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.FAILED);
    assertThat(balanceOf(ctx.payer())).isEqualTo(100);
  }

  @Test
  void duplicateWebhookCapturesOnce() throws Exception {
    Ctx ctx = authorizedPayment(30);
    String eventId = "evt-" + UUID.randomUUID();

    deliver(eventId, ctx.reference(), PspWebhookTypes.PAYMENT_CAPTURED);
    deliver(eventId, ctx.reference(), PspWebhookTypes.PAYMENT_CAPTURED); // redelivery

    assertThat(paymentRepo.findById(ctx.paymentId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.SUCCEEDED);
    assertThat(balanceOf(ctx.payee())).isEqualTo(30); // credited once, not 60
    assertThat(webhookEvents.existsById(eventId)).isTrue();
  }

  private record Ctx(UUID paymentId, UUID payer, UUID payee, String reference) {}

  private Ctx authorizedPayment(long amount) {
    UUID payer =
        accounts
            .createAccount(
                OwnerType.USER, "payer-" + UUID.randomUUID(), AccountType.USER_WALLET, "INR")
            .getId();
    UUID payee =
        accounts
            .createAccount(
                OwnerType.MERCHANT,
                "payee-" + UUID.randomUUID(),
                AccountType.MERCHANT_PAYABLE,
                "INR")
            .getId();
    fund(payer, 100);
    UUID paymentId =
        payments
            .create(
                UUID.randomUUID().toString(),
                new CreatePaymentRequest("m1", payer, payee, amount, "INR"))
            .response()
            .id();
    String reference = paymentRepo.findById(paymentId).orElseThrow().getPspReference();
    return new Ctx(paymentId, payer, payee, reference);
  }

  private void deliver(String eventId, String reference, String type) throws Exception {
    String body = json.writeValueAsString(new PspWebhookEvent(eventId, reference, type));
    webhooks.handle(body, verifier.sign(body));
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
