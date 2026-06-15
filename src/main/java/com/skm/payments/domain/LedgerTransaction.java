package com.skm.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** An immutable journal entry grouping >= 2 postings that net to zero. */
@Entity
@Table(name = "ledger_transactions")
@Getter
@Setter
public class LedgerTransaction {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String type;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
