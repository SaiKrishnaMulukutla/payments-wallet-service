package com.skm.payments.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Drains the outbox to the application log instead of a message broker. The transactional outbox
 * still guarantees the event is recorded atomically with the payment; this is the fan-out sink.
 * Swap for a broker-backed {@link EventPublisher} when downstream consumers are needed.
 */
@Component
public class LoggingEventPublisher implements EventPublisher {

  private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

  @Override
  public void publish(String topic, String key, String payload) {
    log.info("event published stream={} key={} payload={}", topic, key, payload);
  }
}
