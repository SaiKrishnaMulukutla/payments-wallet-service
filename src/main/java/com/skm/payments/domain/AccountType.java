package com.skm.payments.domain;

/** The role an account plays in the ledger. */
public enum AccountType {
  USER_WALLET,
  MERCHANT_PAYABLE,
  PSP_SUSPENSE,
  FEE_INCOME
}
