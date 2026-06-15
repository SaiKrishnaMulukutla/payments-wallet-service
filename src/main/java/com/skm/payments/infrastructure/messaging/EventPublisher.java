package com.skm.payments.infrastructure.messaging;

/** Publishes an event payload to a topic. Abstracted so the relay is testable without a broker. */
public interface EventPublisher {

  /** Publishes synchronously; throws if the broker does not acknowledge. */
  void publish(String topic, String key, String payload);
}
