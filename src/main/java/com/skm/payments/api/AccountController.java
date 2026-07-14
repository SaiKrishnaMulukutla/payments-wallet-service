package com.skm.payments.api;

import com.skm.payments.application.AccountQueryService;
import com.skm.payments.application.BalanceResponse;
import com.skm.payments.application.LedgerPageResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

  private final AccountQueryService accounts;

  public AccountController(AccountQueryService accounts) {
    this.accounts = accounts;
  }

  @GetMapping("/{id}/balance")
  public BalanceResponse balance(@PathVariable UUID id) {
    return accounts.getBalance(id);
  }

  @GetMapping("/{id}/ledger")
  public LedgerPageResponse ledger(
      @PathVariable UUID id, @RequestParam(required = false) Integer limit) {
    return accounts.getLedger(id, limit);
  }
}
