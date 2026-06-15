package com.skm.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Marks an event id as already handled, so redelivered events are skipped (idempotent consumer).
 */
@Entity
@Table(name = "processed_events")
@Getter
@Setter
public class ProcessedEvent {

  @Id
  @Column(name = "event_id")
  private UUID eventId;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt = Instant.now();
}
