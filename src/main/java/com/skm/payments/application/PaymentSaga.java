package com.skm.payments.application;

import com.skm.payments.domain.Account;
import com.skm.payments.domain.AccountType;
import com.skm.payments.domain.Direction;
import com.skm.payments.domain.LedgerService;
import com.skm.payments.domain.LedgerService.Posting;
import com.skm.payments.domain.Payment;
import com.skm.payments.domain.PaymentStatus;
import com.skm.payments.domain.Refund;
import com.skm.payments.domain.RefundStatus;
import com.skm.payments.domain.TransactionType;
import com.skm.payments.repository.AccountRepository;
import com.skm.payments.repository.PaymentRepository;
import com.skm.payments.repository.RefundRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The hold -> settle/reverse saga. Each phase is its own transaction so the (mock) PSP call sits
 * between a committed hold and the settle/reverse — never holding a DB lock across the provider
 * call.
 */
@Service
public class PaymentSaga {

  private final PaymentRepository payments;
  private final AccountRepository accounts;
  private final LedgerService ledger;
  private final OutboxWriter outboxWriter;
  private final RefundRepository refunds;

  public PaymentSaga(
      PaymentRepository payments,
      AccountRepository accounts,
      LedgerService ledger,
      OutboxWriter outboxWriter,
      RefundRepository refunds) {
    this.payments = payments;
    this.accounts = accounts;
    this.ledger = ledger;
    this.outboxWriter = outboxWriter;
    this.refunds = refunds;
  }

  /** Tx1: persist the payment (PROCESSING) and move payer -> suspense. */
  @Transactional
  public Payment hold(UUID paymentId, CreatePaymentRequest request) {
    Payment payment = new Payment();
    payment.setId(paymentId);
    payment.setMerchantId(request.merchantId());
    payment.setPayerAccount(request.payerAccountId());
    payment.setPayeeAccount(request.payeeAccountId());
    payment.setAmount(request.amount());
    payment.setCurrency(request.currency());
    payment.setStatus(PaymentStatus.PROCESSING);
    payment.setCreatedAt(Instant.now());
    payment.setUpdatedAt(Instant.now());
    payments.save(payment);

    ledger.post(
        TransactionType.PAYMENT,
        paymentId,
        List.of(
            new Posting(request.payerAccountId(), Direction.DEBIT, request.amount()),
            new Posting(suspenseAccountId(), Direction.CREDIT, request.amount())));
    return payment;
  }

  /** Tx2 (approved): move suspense -> payee and mark the payment succeeded. */
  @Transactional
  public void settle(UUID paymentId, String pspReference) {
    Payment payment = payments.findById(paymentId).orElseThrow();
    ledger.post(
        TransactionType.PAYMENT,
        paymentId,
        List.of(
            new Posting(suspenseAccountId(), Direction.DEBIT, payment.getAmount()),
            new Posting(payment.getPayeeAccount(), Direction.CREDIT, payment.getAmount())));
    payment.setStatus(PaymentStatus.SUCCEEDED);
    payment.setPspReference(pspReference);
    payment.setUpdatedAt(Instant.now());
    outboxWriter.write(PaymentEventTypes.SUCCEEDED, toEvent(payment));
  }

  /** Tx2 (declined): return suspense -> payer and mark the payment failed. */
  @Transactional
  public void reverse(UUID paymentId) {
    Payment payment = payments.findById(paymentId).orElseThrow();
    ledger.post(
        TransactionType.REVERSAL,
        paymentId,
        List.of(
            new Posting(suspenseAccountId(), Direction.DEBIT, payment.getAmount()),
            new Posting(payment.getPayerAccount(), Direction.CREDIT, payment.getAmount())));
    payment.setStatus(PaymentStatus.FAILED);
    payment.setUpdatedAt(Instant.now());
    outboxWriter.write(PaymentEventTypes.FAILED, toEvent(payment));
  }

  /**
   * PSP authorized but capture is asynchronous: mark the held payment AUTHORIZED, await a webhook.
   */
  @Transactional
  public void authorizePending(UUID paymentId, String pspReference) {
    Payment payment = payments.findById(paymentId).orElseThrow();
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setPspReference(pspReference);
    payment.setUpdatedAt(Instant.now());
  }

  /**
   * Refund (full or partial) a captured payment: move payee -> payer and update the running total.
   */
  @Transactional
  public void refund(UUID refundId, UUID paymentId, long amount) {
    Payment payment =
        payments.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
    if (payment.getStatus() != PaymentStatus.SUCCEEDED
        && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
      throw new RefundNotAllowedException(
          "payment %s is not refundable in status %s".formatted(paymentId, payment.getStatus()));
    }
    long remaining = payment.getAmount() - payment.getRefundedAmount();
    if (amount > remaining) {
      throw new RefundNotAllowedException(
          "refund %d exceeds the remaining refundable amount %d".formatted(amount, remaining));
    }

    ledger.post(
        TransactionType.REFUND,
        paymentId,
        List.of(
            new Posting(payment.getPayeeAccount(), Direction.DEBIT, amount),
            new Posting(payment.getPayerAccount(), Direction.CREDIT, amount)));

    Refund refund = new Refund();
    refund.setId(refundId);
    refund.setPaymentId(paymentId);
    refund.setAmount(amount);
    refund.setStatus(RefundStatus.SUCCEEDED);
    refund.setCreatedAt(Instant.now());
    refunds.save(refund);

    long refundedTotal = payment.getRefundedAmount() + amount;
    payment.setRefundedAmount(refundedTotal);
    payment.setStatus(
        refundedTotal == payment.getAmount()
            ? PaymentStatus.REFUNDED
            : PaymentStatus.PARTIALLY_REFUNDED);
    payment.setUpdatedAt(Instant.now());
    outboxWriter.write(PaymentEventTypes.REFUNDED, toEvent(payment));
  }

  private static PaymentEvent toEvent(Payment payment) {
    return new PaymentEvent(
        UUID.randomUUID(),
        payment.getId(),
        payment.getMerchantId(),
        payment.getStatus(),
        payment.getAmount(),
        payment.getCurrency(),
        Instant.now());
  }

  private UUID suspenseAccountId() {
    Account suspense =
        accounts
            .findFirstByType(AccountType.PSP_SUSPENSE)
            .orElseThrow(() -> new IllegalStateException("no PSP_SUSPENSE account configured"));
    return suspense.getId();
  }
}
