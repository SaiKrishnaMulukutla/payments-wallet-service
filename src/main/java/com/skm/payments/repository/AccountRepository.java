package com.skm.payments.repository;

import com.skm.payments.domain.Account;
import com.skm.payments.domain.AccountType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

  /** Looks up a system account by its role (e.g. the single PSP_SUSPENSE account). */
  Optional<Account> findFirstByType(AccountType type);
}
