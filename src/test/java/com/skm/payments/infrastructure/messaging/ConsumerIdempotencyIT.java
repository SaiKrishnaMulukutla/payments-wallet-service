package com.skm.payments.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.skm.payments.application.PaymentEvent;
import com.skm.payments.domain.PaymentStatus;
import com.skm.payments.repository.ProcessedEventRepository;
import com.skm.payments.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Delivering the same event twice handles it once (idempotent consumer). */
class ConsumerIdempotencyIT extends AbstractIntegrationTest {

  @Autowired PaymentEventConsumer consumer;
  @Autowired ProcessedEventRepository processed;

  @Test
  void duplicateEventIsHandledOnce() {
    UUID eventId = UUID.randomUUID();
    PaymentEvent event =
        new PaymentEvent(
            eventId, UUID.randomUUID(), "m1", PaymentStatus.SUCCEEDED, 30, "INR", Instant.now());

    consumer.handle(event);
    consumer.handle(event); // redelivery — must be a no-op, not a duplicate-key error

    assertThat(processed.existsById(eventId)).isTrue();
  }
}
