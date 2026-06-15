package com.skm.payments.api;

import com.skm.payments.application.RefundRequest;
import com.skm.payments.application.RefundResponse;
import com.skm.payments.application.RefundResult;
import com.skm.payments.application.RefundService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/payments/{paymentId}/refunds")
public class RefundController {

  private final RefundService refunds;

  public RefundController(RefundService refunds) {
    this.refunds = refunds;
  }

  @PostMapping
  public ResponseEntity<RefundResponse> create(
      @PathVariable UUID paymentId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody RefundRequest request) {
    RefundResult result = refunds.refund(idempotencyKey, paymentId, request.amount());
    return ResponseEntity.status(result.statusCode()).body(result.response());
  }
}
