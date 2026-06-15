package com.skm.payments.infrastructure.messaging;

import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes to Kafka and blocks for the broker ack, so a failed send leaves the event unpublished.
 */
@Component
public class KafkaEventPublisher implements EventPublisher {

  private static final long ACK_TIMEOUT_SECONDS = 10;

  private final KafkaTemplate<String, String> kafka;

  public KafkaEventPublisher(KafkaTemplate<String, String> kafka) {
    this.kafka = kafka;
  }

  @Override
  public void publish(String topic, String key, String payload) {
    try {
      kafka.send(topic, key, payload).get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EventPublishException(key, e);
    } catch (Exception e) {
      throw new EventPublishException(key, e);
    }
  }
}
