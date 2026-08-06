package com.skm.payments.infrastructure.messaging;

import com.skm.payments.domain.OutboxEvent;
import com.skm.payments.repository.OutboxRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the outbox and hands unpublished events to the {@link EventPublisher}, marking each
 * published once it is accepted. Delivery is at-least-once: if marking fails after a publish, the
 * event is re-sent next poll. Runs on a schedule in production; tests invoke {@link #publishBatch}
 * directly.
 */
@Component
public class OutboxRelay {

  static final String PAYMENT_EVENTS_STREAM = "payment-events";

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int BATCH_SIZE = 100;

  private final OutboxRepository outbox;
  private final EventPublisher publisher;

  public OutboxRelay(OutboxRepository outbox, EventPublisher publisher) {
    this.outbox = outbox;
    this.publisher = publisher;
  }

  @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:1000}")
  @Transactional
  public int publishBatch() {
    List<OutboxEvent> batch = outbox.lockUnpublishedBatch(BATCH_SIZE);
    int published = 0;
    for (OutboxEvent event : batch) {
      try {
        publisher.publish(
            PAYMENT_EVENTS_STREAM, event.getAggregateId().toString(), event.getPayload());
      } catch (RuntimeException e) {
        log.warn("publish failed for event {}; leaving it for the next poll", event.getId(), e);
        break; // commit what already succeeded; retry the rest later
      }
      event.setPublishedAt(Instant.now());
      published++;
    }
    return published;
  }
}
