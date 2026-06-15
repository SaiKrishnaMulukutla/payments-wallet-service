package com.skm.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.skm.payments.domain.AccountType;
import com.skm.payments.domain.OwnerType;
import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReconciliationIT extends AbstractIntegrationTest {

  @Autowired PaymentService payments;
  @Autowired RefundService refunds;
  @Autowired ReconciliationService reconciliation;
  @Autowired LedgerIntegrityChecker integrity;
  @Autowired AccountService accounts;
  @Autowired AccountBalanceRepository balances;

  @Test
  void ledgerStaysBalancedThroughPaymentAndRefund() {
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
                new CreatePaymentRequest("m1", payer, payee, 50, "INR"))
            .response()
            .id();
    refunds.refund(UUID.randomUUID().toString(), paymentId, 50);

    var report = reconciliation.reconcile();

    // Debits always equal credits, and a ledger-maintained account never drifts.
    assertThat(report.ledgerNet()).isZero();
    assertThat(report.driftedAccounts()).doesNotContain(payee);
  }

  @Test
  void detectsBalanceThatBypassesTheLedger() {
    UUID rogue =
        accounts
            .createAccount(
                OwnerType.USER, "rogue-" + UUID.randomUUID(), AccountType.USER_WALLET, "INR")
            .getId();
    fund(rogue, 100); // balance set without any postings -> must be flagged as drift

    assertThat(integrity.check().driftedAccounts()).contains(rogue);
  }

  private void fund(UUID accountId, long amount) {
    var balance = balances.findById(accountId).orElseThrow();
    balance.setBalance(amount);
    balances.saveAndFlush(balance);
  }
}
