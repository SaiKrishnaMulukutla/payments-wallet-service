package com.skm.payments.application;

import com.skm.payments.domain.IdempotencyRecord;
import com.skm.payments.domain.IdempotencyStatus;
import com.skm.payments.domain.Payment;
import com.skm.payments.infrastructure.psp.AuthorizationResult;
import com.skm.payments.infrastructure.psp.PaymentProvider;
import com.skm.payments.repository.PaymentRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Orchestrates payment creation: idempotency claim/replay, then the hold -> PSP -> settle/reverse
 * saga. Deliberately not {@code @Transactional} — it coordinates several short transactions (claim,
 * hold, settle/reverse, finalize) with the PSP call sitting between committed phases.
 */
@Service
public class PaymentService {

  private static final int CREATED = 201;

  private final IdempotencyService idempotency;
  private final PaymentSaga saga;
  private final PaymentProvider psp;
  private final PaymentRepository payments;

  public PaymentService(
      IdempotencyService idempotency,
      PaymentSaga saga,
      PaymentProvider psp,
      PaymentRepository payments) {
    this.idempotency = idempotency;
    this.saga = saga;
    this.psp = psp;
    this.payments = payments;
  }

  public PaymentResult create(String idempotencyKey, CreatePaymentRequest request) {
    String requestHash = RequestHasher.sha256(canonicalize(request));

    var alreadySeen = idempotency.find(request.merchantId(), idempotencyKey);
    if (alreadySeen.isPresent()) {
      return replayOrReject(alreadySeen.get(), idempotencyKey, requestHash);
    }

    IdempotencyRecord claim;
    try {
      claim = idempotency.open(request.merchantId(), idempotencyKey, requestHash);
    } catch (DataIntegrityViolationException raced) {
      IdempotencyRecord winner =
          idempotency
              .find(request.merchantId(), idempotencyKey)
              .orElseThrow(
                  () -> new IllegalStateException("idempotency record vanished after conflict"));
      return replayOrReject(winner, idempotencyKey, requestHash);
    }

    UUID paymentId = UUID.randomUUID();
    try {
      Payment payment = saga.hold(paymentId, request);
      AuthorizationResult authorization = psp.authorize(payment);
      if (authorization.approved()) {
        saga.settle(paymentId, authorization.reference());
      } else {
        saga.reverse(paymentId);
      }
    } catch (RuntimeException failure) {
      idempotency.release(claim.getId());
      throw failure;
    }

    idempotency.markCompleted(claim.getId(), paymentId, CREATED);
    return new PaymentResult(toResponse(load(paymentId)), CREATED, false);
  }

  public PaymentResponse get(UUID paymentId) {
    return toResponse(load(paymentId));
  }

  private PaymentResult replayOrReject(IdempotencyRecord record, String key, String requestHash) {
    if (!record.getRequestHash().equals(requestHash)) {
      throw new IdempotencyMismatchException(key);
    }
    if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
      throw new IdempotencyConflictException(key);
    }
    return new PaymentResult(
        toResponse(load(record.getResourceId())), record.getResponseCode(), true);
  }

  private Payment load(UUID paymentId) {
    return payments.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
  }

  private static String canonicalize(CreatePaymentRequest r) {
    return String.join(
        "|",
        r.merchantId(),
        String.valueOf(r.payerAccountId()),
        String.valueOf(r.payeeAccountId()),
        String.valueOf(r.amount()),
        r.currency());
  }

  private static PaymentResponse toResponse(Payment p) {
    return new PaymentResponse(
        p.getId(), p.getStatus(), p.getAmount(), p.getCurrency(), p.getCreatedAt());
  }
}
