package com.skm.payments.infrastructure.psp;

/** Outcome of a PSP authorization. */
public record AuthorizationResult(
    AuthorizationOutcome outcome, String reference, String declineReason) {

  public static AuthorizationResult captured(String reference) {
    return new AuthorizationResult(AuthorizationOutcome.CAPTURED, reference, null);
  }

  public static AuthorizationResult pending(String reference) {
    return new AuthorizationResult(AuthorizationOutcome.PENDING, reference, null);
  }

  public static AuthorizationResult declined(String reason) {
    return new AuthorizationResult(AuthorizationOutcome.DECLINED, null, reason);
  }
}
