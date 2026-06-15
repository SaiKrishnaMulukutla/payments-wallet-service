package com.skm.payments.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skm.payments.application.PaymentEvent;
import com.skm.payments.domain.ProcessedEvent;
import com.skm.payments.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Consumes payment events; idempotent via a processed-events dedup table. */
@Component
public class PaymentEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

  private final ProcessedEventRepository processed;
  private final ObjectMapper json;

  public PaymentEventConsumer(ProcessedEventRepository processed, ObjectMapper json) {
    this.processed = processed;
    this.json = json;
  }

  @KafkaListener(
      topics = KafkaConfig.PAYMENT_EVENTS_TOPIC,
      groupId = "${spring.kafka.consumer.group-id:payments}")
  public void onMessage(String payload) {
    handle(deserialize(payload));
  }

  /** Handles an event at most once: a duplicate event id is skipped. */
  @Transactional
  public void handle(PaymentEvent event) {
    if (processed.existsById(event.eventId())) {
      log.debug("event {} already processed; skipping", event.eventId());
      return;
    }
    // Real downstream side effects (notifications, read-model projections) would go here.
    ProcessedEvent marker = new ProcessedEvent();
    marker.setEventId(event.eventId());
    processed.save(marker);
    log.info("processed event {} for payment {}", event.eventId(), event.paymentId());
  }

  private PaymentEvent deserialize(String payload) {
    try {
      return json.readValue(payload, PaymentEvent.class);
    } catch (Exception e) {
      throw new IllegalStateException("failed to deserialize payment event", e);
    }
  }
}
