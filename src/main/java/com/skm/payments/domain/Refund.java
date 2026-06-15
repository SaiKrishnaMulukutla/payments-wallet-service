package com.skm.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** A (full or partial) refund of a captured payment. */
@Entity
@Table(name = "refunds")
@Getter
@Setter
public class Refund {

  @Id private UUID id;

  @Column(name = "payment_id", nullable = false)
  private UUID paymentId;

  @Column(nullable = false)
  private Long amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RefundStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();
}
