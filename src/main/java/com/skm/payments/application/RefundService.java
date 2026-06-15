package com.skm.payments.application;

import com.skm.payments.domain.IdempotencyRecord;
import com.skm.payments.domain.IdempotencyStatus;
import com.skm.payments.domain.Payment;
import com.skm.payments.domain.Refund;
import com.skm.payments.repository.PaymentRepository;
import com.skm.payments.repository.RefundRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Orchestrates refunds: idempotency claim/replay (keyed by the payment's merchant + key), then the
 * transactional ledger reversal in {@link PaymentSaga#refund}. Mirrors {@link PaymentService}.
 */
@Service
public class RefundService {

  private static final int CREATED = 201;

  private final IdempotencyService idempotency;
  private final PaymentSaga saga;
  private final PaymentRepository payments;
  private final RefundRepository refunds;

  public RefundService(
      IdempotencyService idempotency,
      PaymentSaga saga,
      PaymentRepository payments,
      RefundRepository refunds) {
    this.idempotency = idempotency;
    this.saga = saga;
    this.payments = payments;
    this.refunds = refunds;
  }

  public RefundResult refund(String idempotencyKey, UUID paymentId, long amount) {
    Payment payment =
        payments.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
    String merchant = payment.getMerchantId();
    String requestHash = RequestHasher.sha256(paymentId + "|" + amount);

    var alreadySeen = idempotency.find(merchant, idempotencyKey);
    if (alreadySeen.isPresent()) {
      return replayOrReject(alreadySeen.get(), idempotencyKey, requestHash);
    }

    IdempotencyRecord claim;
    try {
      claim = idempotency.open(merchant, idempotencyKey, requestHash);
    } catch (DataIntegrityViolationException raced) {
      IdempotencyRecord winner =
          idempotency
              .find(merchant, idempotencyKey)
              .orElseThrow(
                  () -> new IllegalStateException("idempotency record vanished after conflict"));
      return replayOrReject(winner, idempotencyKey, requestHash);
    }

    UUID refundId = UUID.randomUUID();
    try {
      saga.refund(refundId, paymentId, amount);
    } catch (RuntimeException failure) {
      idempotency.release(claim.getId());
      throw failure;
    }

    idempotency.markCompleted(claim.getId(), refundId, CREATED);
    return new RefundResult(toResponse(loadRefund(refundId)), CREATED, false);
  }

  private RefundResult replayOrReject(IdempotencyRecord record, String key, String requestHash) {
    if (!record.getRequestHash().equals(requestHash)) {
      throw new IdempotencyMismatchException(key);
    }
    if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
      throw new IdempotencyConflictException(key);
    }
    return new RefundResult(
        toResponse(loadRefund(record.getResourceId())), record.getResponseCode(), true);
  }

  private Refund loadRefund(UUID id) {
    return refunds
        .findById(id)
        .orElseThrow(() -> new IllegalStateException("refund not found: " + id));
  }

  private static RefundResponse toResponse(Refund r) {
    return new RefundResponse(
        r.getId(), r.getPaymentId(), r.getAmount(), r.getStatus(), r.getCreatedAt());
  }
}
