package com.skm.payments.application;

/** The webhook signature was missing or did not match. Maps to HTTP 401. */
public class InvalidSignatureException extends RuntimeException {

  public InvalidSignatureException() {
    super("webhook signature is missing or invalid");
  }
}
