package com.skm.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "accounts")
@Getter
@Setter
public class Account {

  @Id private UUID id;

  @Column(name = "owner_type", nullable = false)
  private String ownerType;

  @Column(name = "owner_id", nullable = false)
  private String ownerId;

  @Column(nullable = false)
  private String type;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();
}
