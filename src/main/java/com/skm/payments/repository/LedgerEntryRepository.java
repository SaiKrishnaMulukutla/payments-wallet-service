package com.skm.payments.repository;

import com.skm.payments.domain.LedgerEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

  List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
