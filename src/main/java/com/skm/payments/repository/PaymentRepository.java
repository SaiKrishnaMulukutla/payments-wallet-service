package com.skm.payments.repository;

import com.skm.payments.domain.Payment;
import com.skm.payments.domain.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  long countByPayerAccount(UUID payerAccount);

  Optional<Payment> findByPspReference(String pspReference);

  List<Payment> findByStatusAndUpdatedAtBefore(PaymentStatus status, Instant cutoff);
}
