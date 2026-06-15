package com.skm.payments.application;

/** Inbound PSP webhook payload: which payment (by reference) reached which terminal state. */
public record PspWebhookEvent(String pspEventId, String pspReference, String type) {}
