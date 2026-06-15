package com.skm.payments.repository;

import com.skm.payments.domain.IdempotencyRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, Long> {

  Optional<IdempotencyRecord> findByMerchantIdAndIdemKey(String merchantId, String idemKey);
}
