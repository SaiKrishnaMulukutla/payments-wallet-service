package com.skm.payments.application;

import java.util.UUID;

/** No account exists for the given id. Maps to HTTP 404. */
public class AccountNotFoundException extends RuntimeException {

  public AccountNotFoundException(UUID id) {
    super("account not found: " + id);
  }
}
