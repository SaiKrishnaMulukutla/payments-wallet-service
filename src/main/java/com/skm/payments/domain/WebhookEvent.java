package com.skm.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A received PSP webhook, keyed by the provider's event id so redeliveries are deduped. */
@Entity
@Table(name = "webhook_events")
@Getter
@Setter
public class WebhookEvent {

  @Id
  @Column(name = "psp_event_id")
  private String pspEventId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private WebhookStatus status;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt = Instant.now();
}
