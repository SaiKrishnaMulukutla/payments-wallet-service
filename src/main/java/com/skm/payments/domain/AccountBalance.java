package com.skm.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Materialized balance: a fast-read, continuously-reconciled cache. Postings (ledger_entries)
 * remain the authoritative source of every balance.
 */
@Entity
@Table(name = "account_balances")
@Getter
@Setter
public class AccountBalance {

  @Id
  @Column(name = "account_id")
  private UUID accountId;

  @Column(nullable = false)
  private Long balance = 0L;

  @Version private Long version;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
}
