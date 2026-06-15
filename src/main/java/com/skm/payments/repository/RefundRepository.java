package com.skm.payments.repository;

import com.skm.payments.domain.Refund;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

  long countByPaymentId(UUID paymentId);
}
