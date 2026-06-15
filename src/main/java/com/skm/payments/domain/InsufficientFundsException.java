package com.skm.payments.domain;

import java.util.UUID;

/** Thrown when a debit would drive an account balance negative. Maps to HTTP 422 (M2). */
public class InsufficientFundsException extends RuntimeException {

    private final UUID accountId;

    public InsufficientFundsException(UUID accountId, long balance, long debit) {
        super("insufficient funds in account %s: balance=%d, attempted debit=%d"
                .formatted(accountId, balance, debit));
        this.accountId = accountId;
    }

    public UUID getAccountId() {
        return accountId;
    }
}
