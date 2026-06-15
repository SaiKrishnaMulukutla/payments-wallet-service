package com.skm.payments.infrastructure.psp;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Verifies the HMAC-SHA256 signature of a raw webhook body against a shared secret. */
@Component
public class WebhookSignatureVerifier {

  private static final String ALGORITHM = "HmacSHA256";

  private final byte[] secret;

  public WebhookSignatureVerifier(@Value("${psp.webhook.secret}") String secret) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  /** Constant-time check that {@code providedSignatureHex} is the HMAC of {@code rawBody}. */
  public boolean isValid(String rawBody, String providedSignatureHex) {
    if (rawBody == null || providedSignatureHex == null) {
      return false;
    }
    byte[] expected = sign(rawBody).getBytes(StandardCharsets.US_ASCII);
    byte[] provided = providedSignatureHex.getBytes(StandardCharsets.US_ASCII);
    return MessageDigest.isEqual(expected, provided);
  }

  /** The hex HMAC-SHA256 of the body. Also used by tests to produce a valid signature. */
  public String sign(String rawBody) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret, ALGORITHM));
      return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC algorithm unavailable", e);
    }
  }
}
