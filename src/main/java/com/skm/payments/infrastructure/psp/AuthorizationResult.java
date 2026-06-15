package com.skm.payments.infrastructure.psp;

/** Outcome of a PSP authorization. */
public record AuthorizationResult(boolean approved, String reference, String declineReason) {

  public static AuthorizationResult approved(String reference) {
    return new AuthorizationResult(true, reference, null);
  }

  public static AuthorizationResult declined(String reason) {
    return new AuthorizationResult(false, null, reason);
  }
}
