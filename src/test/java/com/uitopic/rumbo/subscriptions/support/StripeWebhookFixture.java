package com.uitopic.rumbo.subscriptions.support;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
public final class StripeWebhookFixture {
  private StripeWebhookFixture() { }
  public static String randomSecret() {
    byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
  public static String signature(String payload, String secret) throws Exception {
    return signature(payload, secret, Instant.now().getEpochSecond());
  }
  public static String signature(String payload, String secret, long timestamp) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
  }
  public static String payload(String eventId, String type, String orderId, boolean paid) {
    return """
        {"id":"%s","object":"event","api_version":"2020-08-27","type":"%s",
         "data":{"object":{"id":"cs_test","object":"checkout.session",
         "client_reference_id":"%s","payment_status":"%s","subscription":"sub_test"}}}
        """.formatted(eventId, type, orderId, paid ? "paid" : "unpaid");
  }
}
