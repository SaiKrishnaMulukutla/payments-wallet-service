package com.skm.payments.repository;

import com.skm.payments.domain.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

  /**
   * Locks a batch of unpublished events for the current transaction. {@code FOR UPDATE SKIP LOCKED}
   * lets multiple relay instances drain the outbox without stepping on each other.
   */
  @Query(
      value =
          "SELECT * FROM outbox WHERE published_at IS NULL "
              + "ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT :limit",
      nativeQuery = true)
  List<OutboxEvent> lockUnpublishedBatch(@Param("limit") int limit);
}
