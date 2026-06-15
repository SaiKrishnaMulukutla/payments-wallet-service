package com.skm.payments.application;

/**
 * The payment can't be refunded (wrong status, or amount exceeds the refundable balance). HTTP 422.
 */
public class RefundNotAllowedException extends RuntimeException {

  public RefundNotAllowedException(String message) {
    super(message);
  }
}
