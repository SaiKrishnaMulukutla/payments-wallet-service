package com.skm.payments.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skm.payments.application.AccountService;
import com.skm.payments.application.CreatePaymentRequest;
import com.skm.payments.application.PaymentService;
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
class RefundControllerIT extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired PaymentService payments;
  @Autowired AccountService accounts;
  @Autowired AccountBalanceRepository balances;

  @Test
  void refundReturns201() throws Exception {
    UUID paymentId = capturedPayment(50);
    mvc.perform(
            post("/v1/payments/{id}/refunds", paymentId)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":50}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amount").value(50))
        .andExpect(jsonPath("$.status").value("SUCCEEDED"));
  }

  @Test
  void overRefundReturns422() throws Exception {
    UUID paymentId = capturedPayment(50);
    mvc.perform(
            post("/v1/payments/{id}/refunds", paymentId)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":60}"))
        .andExpect(status().isUnprocessableEntity());
  }

  private UUID capturedPayment(long amount) {
    UUID payer =
        accounts
            .createAccount(
                OwnerType.USER, "payer-" + UUID.randomUUID(), AccountType.USER_WALLET, "INR")
            .getId();
    UUID payee =
        accounts
            .createAccount(
                OwnerType.MERCHANT,
                "payee-" + UUID.randomUUID(),
                AccountType.MERCHANT_PAYABLE,
                "INR")
            .getId();
    var balance = balances.findById(payer).orElseThrow();
    balance.setBalance(100L);
    balances.saveAndFlush(balance);
    return payments
        .create(
            UUID.randomUUID().toString(),
            new CreatePaymentRequest("m1", payer, payee, amount, "INR"))
        .response()
        .id();
  }
}
