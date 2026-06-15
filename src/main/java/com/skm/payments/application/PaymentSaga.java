package com.skm.payments.application;

import com.skm.payments.domain.Account;
import com.skm.payments.domain.AccountType;
import com.skm.payments.domain.Direction;
import com.skm.payments.domain.LedgerService;
import com.skm.payments.domain.LedgerService.Posting;
import com.skm.payments.domain.Payment;
import com.skm.payments.domain.PaymentStatus;
import com.skm.payments.domain.TransactionType;
import com.skm.payments.repository.AccountRepository;
import com.skm.payments.repository.PaymentRepository;
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

  public PaymentSaga(
      PaymentRepository payments,
      AccountRepository accounts,
      LedgerService ledger,
      OutboxWriter outboxWriter) {
    this.payments = payments;
    this.accounts = accounts;
    this.ledger = ledger;
    this.outboxWriter = outboxWriter;
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
