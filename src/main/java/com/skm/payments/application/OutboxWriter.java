package com.skm.payments.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skm.payments.domain.OutboxEvent;
import com.skm.payments.repository.OutboxRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Writes an outbox row. Called from within the saga's transaction so the event is persisted
 * atomically with the payment state change — the row is only published once that transaction
 * commits.
 */
@Component
public class OutboxWriter {

  private static final String AGGREGATE_TYPE = "PAYMENT";

  private final OutboxRepository outbox;
  private final ObjectMapper json;

  public OutboxWriter(OutboxRepository outbox, ObjectMapper json) {
    this.outbox = outbox;
    this.json = json;
  }

  public void write(String eventType, PaymentEvent event) {
    OutboxEvent row = new OutboxEvent();
    row.setId(event.eventId());
    row.setAggregateType(AGGREGATE_TYPE);
    row.setAggregateId(event.paymentId());
    row.setEventType(eventType);
    row.setPayload(serialize(event));
    row.setCreatedAt(Instant.now());
    outbox.save(row);
  }

  private String serialize(PaymentEvent event) {
    try {
      return json.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("failed to serialize payment event", e);
    }
  }
}
