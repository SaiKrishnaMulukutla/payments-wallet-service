package com.skm.payments.application;

import com.skm.payments.domain.IdempotencyRecord;
import com.skm.payments.domain.IdempotencyStatus;
import com.skm.payments.repository.IdempotencyRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable idempotency. {@link #open} claims a key by inserting an IN_PROGRESS row; the unique
 * (merchant_id, idem_key) constraint makes concurrent claims race-safe — exactly one wins, the rest
 * get a {@link org.springframework.dao.DataIntegrityViolationException} for the caller to handle.
 */
@Service
public class IdempotencyService {

  private final IdempotencyRepository records;

  public IdempotencyService(IdempotencyRepository records) {
    this.records = records;
  }

  @Transactional
  public IdempotencyRecord open(String merchantId, String idemKey, String requestHash) {
    IdempotencyRecord record = new IdempotencyRecord();
    record.setMerchantId(merchantId);
    record.setIdemKey(idemKey);
    record.setRequestHash(requestHash);
    record.setStatus(IdempotencyStatus.IN_PROGRESS);
    record.setCreatedAt(Instant.now());
    // saveAndFlush surfaces a duplicate-key violation now, inside this transaction.
    return records.saveAndFlush(record);
  }

  @Transactional(readOnly = true)
  public Optional<IdempotencyRecord> find(String merchantId, String idemKey) {
    return records.findByMerchantIdAndIdemKey(merchantId, idemKey);
  }

  @Transactional
  public void markCompleted(Long id, UUID resourceId, int responseCode) {
    IdempotencyRecord record = records.findById(id).orElseThrow();
    record.setStatus(IdempotencyStatus.COMPLETED);
    record.setResourceId(resourceId);
    record.setResponseCode(responseCode);
  }

  /** Releases a claimed key whose operation failed, so a genuine retry can re-attempt. */
  @Transactional
  public void release(Long id) {
    records.deleteById(id);
  }
}
