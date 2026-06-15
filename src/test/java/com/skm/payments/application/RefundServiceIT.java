package com.skm.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.skm.payments.domain.AccountType;
import com.skm.payments.domain.OwnerType;
import com.skm.payments.domain.PaymentStatus;
import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.repository.OutboxRepository;
import com.skm.payments.repository.PaymentRepository;
import com.skm.payments.repository.RefundRepository;
import com.skm.payments.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RefundServiceIT extends AbstractIntegrationTest {

  @Autowired PaymentService payments;
  @Autowired RefundService refunds;
  @Autowired AccountService accounts;
  @Autowired AccountBalanceRepository balances;
  @Autowired PaymentRepository paymentRepo;
  @Autowired RefundRepository refundRepo;
  @Autowired OutboxRepository outbox;

  @Test
  void fullRefundReturnsFundsAndMarksRefunded() {
    Ctx c = capturedPayment(50);

    refunds.refund(UUID.randomUUID().toString(), c.paymentId(), 50);

    assertThat(paymentRepo.findById(c.paymentId()).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.REFUNDED);
    assertThat(balanceOf(c.payer())).isEqualTo(100);
    assertThat(balanceOf(c.payee())).isZero();
    assertThat(
            outbox.findAll().stream()
                .anyMatch(
                    e ->
                        e.getAggregateId().equals(c.paymentId())
                            && e.getEventType().equals(PaymentEventTypes.REFUNDED)))
        .isTrue();
  }

  @Test
  void partialRefundTracksRemaining() {
    Ctx c = capturedPayment(50);

    refunds.refund(UUID.randomUUID().toString(), c.paymentId(), 20);

    var payment = paymentRepo.findById(c.paymentId()).orElseThrow();
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    assertThat(payment.getRefundedAmount()).isEqualTo(20);
    assertThat(balanceOf(c.payer())).isEqualTo(70);
    assertThat(balanceOf(c.payee())).isEqualTo(30);
  }

  @Test
  void overRefundIsRejected() {
    Ctx c = capturedPayment(50);

    assertThatThrownBy(() -> refunds.refund(UUID.randomUUID().toString(), c.paymentId(), 60))
        .isInstanceOf(RefundNotAllowedException.class);

    assertThat(balanceOf(c.payer())).isEqualTo(50);
    assertThat(balanceOf(c.payee())).isEqualTo(50);
    assertThat(refundRepo.countByPaymentId(c.paymentId())).isZero();
  }

  @Test
  void sameKeyRefundsOnce() {
    Ctx c = capturedPayment(50);
    String key = UUID.randomUUID().toString();

    refunds.refund(key, c.paymentId(), 50);
    var replay = refunds.refund(key, c.paymentId(), 50);

    assertThat(replay.replayed()).isTrue();
    assertThat(refundRepo.countByPaymentId(c.paymentId())).isEqualTo(1);
    assertThat(balanceOf(c.payer())).isEqualTo(100);
  }

  private record Ctx(UUID paymentId, UUID payer, UUID payee) {}

  private Ctx capturedPayment(long amount) {
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
    return new Ctx(paymentId, payer, payee);
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
