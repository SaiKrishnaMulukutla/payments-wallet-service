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

/** An immutable journal entry grouping >= 2 postings that net to zero. */
@Entity
@Table(name = "ledger_transactions")
@Getter
@Setter
public class LedgerTransaction {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TransactionType type;

  @Column(name = "reference_id", nullable = false)
  private UUID referenceId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();
}
