package com.skm.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.skm.payments.domain.AccountType;
import com.skm.payments.domain.InsufficientFundsException;
import com.skm.payments.domain.OwnerType;
import com.skm.payments.domain.PaymentStatus;
import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.repository.PaymentRepository;
import com.skm.payments.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentServiceIT extends AbstractIntegrationTest {

  @Autowired PaymentService payments;
  @Autowired AccountService accounts;
  @Autowired AccountBalanceRepository balances;
  @Autowired PaymentRepository paymentRepo;

  @Test
  void approvedPaymentMovesFundsAndSucceeds() {
    var payer = wallet();
    var payee = merchant();
    fund(payer, 100);

    var result =
        payments.create(
            UUID.randomUUID().toString(), new CreatePaymentRequest("m1", payer, payee, 30, "INR"));

    assertThat(result.response().status()).isEqualTo(PaymentStatus.SUCCEEDED);
    assertThat(result.replayed()).isFalse();
    assertThat(balanceOf(payer)).isEqualTo(70);
    assertThat(balanceOf(payee)).isEqualTo(30);
    assertThat(paymentRepo.countByPayerAccount(payer)).isEqualTo(1);
  }

  @Test
  void insufficientFundsIsRejectedAndNothingPersists() {
    var payer = wallet();
    var payee = merchant();
    fund(payer, 10);

    assertThatThrownBy(
            () ->
                payments.create(
                    UUID.randomUUID().toString(),
                    new CreatePaymentRequest("m1", payer, payee, 50, "INR")))
        .isInstanceOf(InsufficientFundsException.class);

    assertThat(balanceOf(payer)).isEqualTo(10);
    assertThat(balanceOf(payee)).isEqualTo(0);
    assertThat(paymentRepo.countByPayerAccount(payer)).isZero();
  }

  @Test
  void sameKeyReplaysTheSamePayment() {
    var payer = wallet();
    var payee = merchant();
    fund(payer, 100);
    var key = UUID.randomUUID().toString();
    var request = new CreatePaymentRequest("m1", payer, payee, 30, "INR");

    var first = payments.create(key, request);
    var second = payments.create(key, request);

    assertThat(second.replayed()).isTrue();
    assertThat(second.response().id()).isEqualTo(first.response().id());
    assertThat(paymentRepo.countByPayerAccount(payer)).isEqualTo(1);
    assertThat(balanceOf(payer)).isEqualTo(70);
  }

  @Test
  void sameKeyDifferentBodyIsRejected() {
    var payer = wallet();
    var payee = merchant();
    fund(payer, 100);
    var key = UUID.randomUUID().toString();

    payments.create(key, new CreatePaymentRequest("m1", payer, payee, 30, "INR"));

    assertThatThrownBy(
            () -> payments.create(key, new CreatePaymentRequest("m1", payer, payee, 31, "INR")))
        .isInstanceOf(IdempotencyMismatchException.class);
  }

  private UUID wallet() {
    return accounts
        .createAccount(OwnerType.USER, "payer-" + UUID.randomUUID(), AccountType.USER_WALLET, "INR")
        .getId();
  }

  private UUID merchant() {
    return accounts
        .createAccount(
            OwnerType.MERCHANT, "payee-" + UUID.randomUUID(), AccountType.MERCHANT_PAYABLE, "INR")
        .getId();
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
