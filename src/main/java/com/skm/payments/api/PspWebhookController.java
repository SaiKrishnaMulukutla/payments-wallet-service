package com.skm.payments.api;

import com.skm.payments.application.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/webhooks/psp")
public class PspWebhookController {

  private final WebhookService webhooks;

  public PspWebhookController(WebhookService webhooks) {
    this.webhooks = webhooks;
  }

  @PostMapping
  public ResponseEntity<Void> receive(
      @RequestHeader(value = "X-PSP-Signature", required = false) String signature,
      @RequestBody String rawBody) {
    // Verify over the raw body, then parse — a missing header is treated as an invalid signature.
    webhooks.handle(rawBody, signature);
    return ResponseEntity.ok().build();
  }
}
