package com.skm.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.skm.payments.application.AccountService;
import com.skm.payments.domain.LedgerService.Posting;
import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LedgerServiceIT extends AbstractIntegrationTest {

  @Autowired LedgerService ledger;
  @Autowired AccountService accounts;
  @Autowired AccountBalanceRepository balances;

  @Test
  void transferMovesBalances() {
    var payer =
        accounts.createAccount(
            OwnerType.USER, "u-" + UUID.randomUUID(), AccountType.USER_WALLET, "INR");
    var payee =
        accounts.createAccount(
            OwnerType.USER, "u-" + UUID.randomUUID(), AccountType.USER_WALLET, "INR");
    fund(payer.getId(), 100);

    ledger.post(
        TransactionType.PAYMENT,
        UUID.randomUUID(),
        List.of(
            new Posting(payer.getId(), Direction.DEBIT, 30),
            new Posting(payee.getId(), Direction.CREDIT, 30)));

    assertThat(balanceOf(payer.getId())).isEqualTo(70);
    assertThat(balanceOf(payee.getId())).isEqualTo(30);
  }

  @Test
  void insufficientFundsRollsBackEverything() {
    var payer =
        accounts.createAccount(
            OwnerType.USER, "u-" + UUID.randomUUID(), AccountType.USER_WALLET, "INR");
    var sink =
        accounts.createAccount(
            OwnerType.USER, "u-" + UUID.randomUUID(), AccountType.USER_WALLET, "INR");
    fund(payer.getId(), 10);

    assertThatThrownBy(
            () ->
                ledger.post(
                    TransactionType.PAYMENT,
                    UUID.randomUUID(),
                    List.of(
                        new Posting(payer.getId(), Direction.DEBIT, 20),
                        new Posting(sink.getId(), Direction.CREDIT, 20))))
        .isInstanceOf(InsufficientFundsException.class);

    assertThat(balanceOf(payer.getId())).isEqualTo(10);
    assertThat(balanceOf(sink.getId())).isEqualTo(0);
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
