package com.skm.payments.infrastructure.messaging;

/** Raised when an event could not be published; the relay leaves the row to retry next poll. */
public class EventPublishException extends RuntimeException {

  public EventPublishException(String key, Throwable cause) {
    super("failed to publish event with key " + key, cause);
  }
}
