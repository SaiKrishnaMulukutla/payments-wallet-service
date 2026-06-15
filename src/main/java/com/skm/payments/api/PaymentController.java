package com.skm.payments.api;

import com.skm.payments.application.CreatePaymentRequest;
import com.skm.payments.application.PaymentResponse;
import com.skm.payments.application.PaymentResult;
import com.skm.payments.application.PaymentService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

  private final PaymentService payments;

  public PaymentController(PaymentService payments) {
    this.payments = payments;
  }

  @PostMapping
  public ResponseEntity<PaymentResponse> create(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CreatePaymentRequest request) {
    PaymentResult result = payments.create(idempotencyKey, request);
    return ResponseEntity.status(result.statusCode()).body(result.response());
  }

  @GetMapping("/{id}")
  public PaymentResponse get(@PathVariable UUID id) {
    return payments.get(id);
  }
}
