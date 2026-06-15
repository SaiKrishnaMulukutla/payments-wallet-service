package com.skm.payments.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skm.payments.domain.Payment;
import com.skm.payments.domain.PaymentStatus;
import com.skm.payments.domain.WebhookEvent;
import com.skm.payments.domain.WebhookStatus;
import com.skm.payments.infrastructure.psp.WebhookSignatureVerifier;
import com.skm.payments.repository.PaymentRepository;
import com.skm.payments.repository.WebhookEventRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles inbound PSP webhooks: verify signature, dedupe on the provider event id, then capture or
 * fail the matching authorized payment. Idempotent twice over — the event-id dedup and a state
 * guard (only an AUTHORIZED payment is acted on) — so redelivery can never double-capture.
 */
@Service
public class WebhookService {

  private final WebhookSignatureVerifier verifier;
  private final WebhookEventRepository webhookEvents;
  private final PaymentRepository payments;
  private final PaymentSaga saga;
  private final ObjectMapper json;

  public WebhookService(
      WebhookSignatureVerifier verifier,
      WebhookEventRepository webhookEvents,
      PaymentRepository payments,
      PaymentSaga saga,
      ObjectMapper json) {
    this.verifier = verifier;
    this.webhookEvents = webhookEvents;
    this.payments = payments;
    this.saga = saga;
    this.json = json;
  }

  @Transactional
  public void handle(String rawBody, String signature) {
    if (!verifier.isValid(rawBody, signature)) {
      throw new InvalidSignatureException();
    }
    PspWebhookEvent event = parse(rawBody);
    if (webhookEvents.existsById(event.pspEventId())) {
      return; // already handled — idempotent no-op
    }
    record(event, rawBody);
    capture(event);
  }

  private void capture(PspWebhookEvent event) {
    Optional<Payment> match = payments.findByPspReference(event.pspReference());
    if (match.isEmpty()) {
      return; // unknown reference — acknowledge and ignore
    }
    Payment payment = match.get();
    if (payment.getStatus() != PaymentStatus.AUTHORIZED) {
      return; // state guard: only an authorized payment can be captured or failed
    }
    switch (event.type()) {
      case PspWebhookTypes.PAYMENT_CAPTURED ->
          saga.settle(payment.getId(), payment.getPspReference());
      case PspWebhookTypes.PAYMENT_FAILED -> saga.reverse(payment.getId());
      default -> {
        // unknown event type — ignore
      }
    }
  }

  private void record(PspWebhookEvent event, String rawBody) {
    WebhookEvent received = new WebhookEvent();
    received.setPspEventId(event.pspEventId());
    received.setPayload(rawBody);
    received.setStatus(WebhookStatus.PROCESSED);
    webhookEvents.save(received);
  }

  private PspWebhookEvent parse(String rawBody) {
    try {
      return json.readValue(rawBody, PspWebhookEvent.class);
    } catch (Exception e) {
      throw new IllegalStateException("invalid webhook payload", e);
    }
  }
}
