package com.skm.payments.application;

import com.skm.payments.domain.Account;
import com.skm.payments.domain.AccountBalance;
import com.skm.payments.domain.AccountType;
import com.skm.payments.domain.OwnerType;
import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.repository.AccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates accounts together with their zero-initialized balance row. */
@Service
public class AccountService {

  private final AccountRepository accounts;
  private final AccountBalanceRepository balances;

  public AccountService(AccountRepository accounts, AccountBalanceRepository balances) {
    this.accounts = accounts;
    this.balances = balances;
  }

  @Transactional
  public Account createAccount(
      OwnerType ownerType, String ownerId, AccountType type, String currency) {
    Account account = new Account();
    account.setId(UUID.randomUUID());
    account.setOwnerType(ownerType);
    account.setOwnerId(ownerId);
    account.setType(type);
    account.setCurrency(currency);
    account.setCreatedAt(Instant.now());
    accounts.save(account);

    AccountBalance balance = new AccountBalance();
    balance.setAccountId(account.getId());
    balance.setBalance(0L);
    balance.setUpdatedAt(Instant.now());
    balances.save(balance);

    return account;
  }
}
