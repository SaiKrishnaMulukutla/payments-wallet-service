package com.skm.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.skm.payments.domain.AccountType;
import com.skm.payments.domain.OwnerType;
import com.skm.payments.domain.Payment;
import com.skm.payments.domain.PaymentStatus;
import com.skm.payments.infrastructure.psp.AuthorizationResult;
import com.skm.payments.infrastructure.psp.PaymentProvider;
import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.repository.PaymentRepository;
import com.skm.payments.support.AbstractIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Reconciliation expires AUTHORIZED holds whose capture never arrived, returning funds to payer.
 */
@Import(StaleHoldExpiryIT.PendingPspConfig.class)
class StaleHoldExpiryIT extends AbstractIntegrationTest {

  @Autowired PaymentService payments;
  @Autowired ReconciliationService reconciliation;
  @Autowired AccountService accounts;
  @Autowired AccountBalanceRepository balances;
  @Autowired PaymentRepository paymentRepo;

  @TestConfiguration
  static class PendingPspConfig {
    @Bean
    @Primary
    PaymentProvider pendingPsp() {
      return payment -> AuthorizationResult.pending("psp_" + UUID.randomUUID());
    }
  }

  @Test
  void staleAuthorizedHoldIsExpired() {
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
                new CreatePaymentRequest("m1", payer, payee, 30, "INR"))
            .response()
            .id();
    assertThat(paymentRepo.findById(paymentId).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.AUTHORIZED);

    backdate(paymentId, Instant.now().minus(2, ChronoUnit.HOURS));
    var report = reconciliation.reconcile();

    assertThat(report.expiredHolds()).isGreaterThanOrEqualTo(1);
    assertThat(paymentRepo.findById(paymentId).orElseThrow().getStatus())
        .isEqualTo(PaymentStatus.FAILED);
    assertThat(balanceOf(payer)).isEqualTo(100); // hold returned
  }

  private void backdate(UUID paymentId, Instant when) {
    Payment payment = paymentRepo.findById(paymentId).orElseThrow();
    payment.setUpdatedAt(when);
    paymentRepo.saveAndFlush(payment);
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
