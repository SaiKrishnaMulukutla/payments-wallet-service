package com.skm.payments.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skm.payments.application.AccountService;
import com.skm.payments.application.CreatePaymentRequest;
import com.skm.payments.domain.AccountType;
import com.skm.payments.domain.OwnerType;
import com.skm.payments.repository.AccountBalanceRepository;
import com.skm.payments.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PaymentControllerIT extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired AccountService accounts;
  @Autowired AccountBalanceRepository balances;

  @Test
  void createReturns201AndProcessesPayment() throws Exception {
    var payer = wallet();
    var payee = merchant();
    fund(payer, 100);
    var body = json.writeValueAsString(new CreatePaymentRequest("m1", payer, payee, 30, "INR"));

    mvc.perform(
            post("/v1/payments")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.amount").value(30));
  }

  @Test
  void missingIdempotencyKeyIsBadRequest() throws Exception {
    var payer = wallet();
    var payee = merchant();
    fund(payer, 100);
    var body = json.writeValueAsString(new CreatePaymentRequest("m1", payer, payee, 30, "INR"));

    mvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  private UUID wallet() {
    return accounts
        .createAccount(OwnerType.USER, "payer-" + UUID.randomUUID(), AccountType.USER_WALLET, "INR")
        .getId();
  }

  private UUID merchant() {
    return accounts
        .createAccount(
            OwnerType.MERCHANT, "payee-" + UUID.randomUUID(), AccountType.MERCHANT_PAYABLE, "INR")
        .getId();
  }

  private void fund(UUID accountId, long amount) {
    var balance = balances.findById(accountId).orElseThrow();
    balance.setBalance(amount);
    balances.saveAndFlush(balance);
  }
}
