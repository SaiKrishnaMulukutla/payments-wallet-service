package com.skm.payments.repository;

import com.skm.payments.domain.AccountBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, UUID> {

    /**
     * Pessimistic SELECT ... FOR UPDATE on the balance row being debited.
     * Serializes concurrent debits per account so a wallet cannot be oversold.
     * (Wired here for M1; used by the debit path in PaymentService.)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from AccountBalance b where b.accountId = :accountId")
    Optional<AccountBalance> findByIdForUpdate(@Param("accountId") UUID accountId);
}
