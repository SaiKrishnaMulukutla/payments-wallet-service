package com.skm.payments.application;

import com.skm.payments.domain.Payment;
import com.skm.payments.domain.PaymentStatus;
import com.skm.payments.repository.PaymentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodic safety net: asserts ledger integrity (debits = credits, balances match postings) and
 * expires stale AUTHORIZED holds whose capture webhook never arrived (returning funds to the
 * payer). Runs on a schedule in production; tests invoke {@link #reconcile} directly.
 */
@Service
public class ReconciliationService {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

  private final LedgerIntegrityChecker integrity;
  private final PaymentRepository payments;
  private final PaymentSaga saga;
  private final Duration authorizedTimeout;

  public ReconciliationService(
      LedgerIntegrityChecker integrity,
      PaymentRepository payments,
      PaymentSaga saga,
      @Value("${reconciliation.authorized-timeout:30m}") Duration authorizedTimeout) {
    this.integrity = integrity;
    this.payments = payments;
    this.saga = saga;
    this.authorizedTimeout = authorizedTimeout;
  }

  @Scheduled(fixedDelayString = "${reconciliation.delay-ms:60000}")
  public void scheduledRun() {
    reconcile();
  }

  public ReconciliationReport reconcile() {
    LedgerIntegrityChecker.Result result = integrity.check();
    if (!result.balanced()) {
      log.error(
          "LEDGER IMBALANCE detected: net={} driftedAccounts={}",
          result.ledgerNet(),
          result.driftedAccounts());
    }
    int expired = expireStaleHolds();
    return new ReconciliationReport(result.ledgerNet(), result.driftedAccounts(), expired);
  }

  private int expireStaleHolds() {
    Instant cutoff = Instant.now().minus(authorizedTimeout);
    List<Payment> stale = payments.findByStatusAndUpdatedAtBefore(PaymentStatus.AUTHORIZED, cutoff);
    for (Payment payment : stale) {
      log.warn(
          "expiring stale authorized payment {} (no capture within {})",
          payment.getId(),
          authorizedTimeout);
      saga.reverse(payment.getId());
    }
    return stale.size();
  }
}
