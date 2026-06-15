package com.skm.payments.domain;

/** Whether an idempotent request is still running or has finished. */
public enum IdempotencyStatus {
  IN_PROGRESS,
  COMPLETED
}
