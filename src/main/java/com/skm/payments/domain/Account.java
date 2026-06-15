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

@Entity
@Table(name = "accounts")
@Getter
@Setter
public class Account {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "owner_type", nullable = false)
  private OwnerType ownerType;

  @Column(name = "owner_id", nullable = false)
  private String ownerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AccountType type;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();
}
