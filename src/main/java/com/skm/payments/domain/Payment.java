package com.skm.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** A payment moving funds payer -> payee, realized as ledger transactions. */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment {

  @Id private UUID id;

  @Column(name = "merchant_id", nullable = false)
  private String merchantId;

  @Column(name = "payer_account", nullable = false)
  private UUID payerAccount;

  @Column(name = "payee_account", nullable = false)
  private UUID payeeAccount;

  @Column(nullable = false)
  private Long amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentStatus status;

  @Column(name = "psp_reference")
  private String pspReference;

  @Version private Long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();
}
