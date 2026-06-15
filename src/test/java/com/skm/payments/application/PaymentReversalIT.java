package com.skm.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.skm.payments.domain.AccountType;
import com.skm.payments.domain.OwnerType;
import com.skm.payments.domain.PaymentStatus;
import com.skm.payments.infrastructure.psp.AuthorizationResult;
import com.skm.payments.infrastructure.psp.PaymentProvider;
import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/** Verifies the declined-PSP path reverses the hold and leaves the payer whole. */
@Import(PaymentReversalIT.DecliningPspConfig.class)
class PaymentReversalIT extends AbstractIntegrationTest {

  @Autowired PaymentService payments;
  @Autowired AccountService accounts;
  @Autowired AccountBalanceRepository balances;

  @TestConfiguration
  static class DecliningPspConfig {
    @Bean
    @Primary
    PaymentProvider decliningPsp() {
      return payment -> AuthorizationResult.declined("test decline");
    }
  }

  @Test
  void declinedPaymentReversesHoldAndFails() {
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

    var result =
        payments.create(
            UUID.randomUUID().toString(), new CreatePaymentRequest("m1", payer, payee, 40, "INR"));

    assertThat(result.response().status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(balanceOf(payer)).as("payer made whole after reversal").isEqualTo(100);
    assertThat(balanceOf(payee)).isEqualTo(0);
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
