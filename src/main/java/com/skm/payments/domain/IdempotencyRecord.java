package com.skm.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Durable record of an idempotent request, keyed uniquely by (merchant_id, idem_key). Makes a
 * non-safe POST retry-safe: the first request claims the key and stores its outcome; later requests
 * with the same key replay it instead of re-running the operation.
 */
@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
public class IdempotencyRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "merchant_id", nullable = false)
  private String merchantId;

  @Column(name = "idem_key", nullable = false)
  private String idemKey;

  @Column(name = "request_hash", nullable = false)
  private String requestHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private IdempotencyStatus status;

  @Column(name = "resource_id")
  private UUID resourceId;

  @Column(name = "response_code")
  private Integer responseCode;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();
}
