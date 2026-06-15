package com.skm.payments.application;

import com.skm.payments.domain.Account;
import com.skm.payments.domain.AccountBalance;
import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

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
    public Account createAccount(String ownerType, String ownerId, String type, String currency) {
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
