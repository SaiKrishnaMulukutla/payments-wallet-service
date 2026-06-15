package com.skm.payments.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.skm.payments.infrastructure.psp.WebhookSignatureVerifier;
import com.skm.payments.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PspWebhookControllerIT extends AbstractIntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired WebhookSignatureVerifier verifier;

  @Test
  void validSignatureReturns200() throws Exception {
    String body = webhookBody(UUID.randomUUID().toString(), "unknown-ref");
    mvc.perform(
            post("/v1/webhooks/psp")
                .header("X-PSP-Signature", verifier.sign(body))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void invalidSignatureReturns401() throws Exception {
    String body = webhookBody("evt-bad", "ref");
    mvc.perform(
            post("/v1/webhooks/psp")
                .header("X-PSP-Signature", "deadbeef")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void missingSignatureReturns401() throws Exception {
    String body = webhookBody("evt-missing", "ref");
    mvc.perform(post("/v1/webhooks/psp").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
  }

  private static String webhookBody(String eventId, String reference) {
    return "{\"pspEventId\":\"%s\",\"pspReference\":\"%s\",\"type\":\"payment.captured\"}"
        .formatted(eventId, reference);
  }
}
