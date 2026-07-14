package com.skm.payments.api;

import com.skm.payments.application.IntegrityResponse;
import com.skm.payments.application.LedgerIntegrityChecker;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only ledger-integrity report. Access control (admin-only) is enforced by the caller/gateway;
 * this endpoint exposes only the integrity summary, never raw account data.
 */
@RestController
@RequestMapping("/v1/reconciliation")
public class ReconciliationController {

  private final LedgerIntegrityChecker integrity;

  public ReconciliationController(LedgerIntegrityChecker integrity) {
    this.integrity = integrity;
  }

  @GetMapping("/report")
  public IntegrityResponse report() {
    LedgerIntegrityChecker.Result result = integrity.check();
    return new IntegrityResponse(
        result.balanced(), result.ledgerNet(), result.driftedAccounts().size());
  }
}
