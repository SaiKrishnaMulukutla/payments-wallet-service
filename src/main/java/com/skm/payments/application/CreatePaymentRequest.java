package com.skm.payments.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request to create a payment moving {@code amount} (minor units) from payer to payee. */
public record CreatePaymentRequest(
    @NotBlank String merchantId,
    @NotNull UUID payerAccountId,
    @NotNull UUID payeeAccountId,
    @Positive long amount,
    @NotBlank @Size(min = 3, max = 3) String currency) {}
