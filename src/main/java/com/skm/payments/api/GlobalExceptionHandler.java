package com.skm.payments.api;

import com.skm.payments.application.IdempotencyConflictException;
import com.skm.payments.application.IdempotencyMismatchException;
import com.skm.payments.application.InvalidSignatureException;
import com.skm.payments.application.PaymentNotFoundException;
import com.skm.payments.application.RefundNotAllowedException;
import com.skm.payments.domain.InsufficientFundsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain/application exceptions to RFC-7807 problem responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(InsufficientFundsException.class)
  ProblemDetail handleInsufficientFunds(InsufficientFundsException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  ProblemDetail handleConflict(IdempotencyConflictException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(IdempotencyMismatchException.class)
  ProblemDetail handleMismatch(IdempotencyMismatchException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
  }

  @ExceptionHandler(PaymentNotFoundException.class)
  ProblemDetail handleNotFound(PaymentNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(InvalidSignatureException.class)
  ProblemDetail handleInvalidSignature(InvalidSignatureException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
  }

  @ExceptionHandler(RefundNotAllowedException.class)
  ProblemDetail handleRefundNotAllowed(RefundNotAllowedException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
  }
}
