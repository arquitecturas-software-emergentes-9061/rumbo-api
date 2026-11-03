package com.uitopic.rumbo.iam.infrastructure.tokens;

import com.uitopic.rumbo.iam.application.ports.SecureTokenGenerator;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 256-bit random tokens encoded as URL-safe Base64, stored as their SHA-256 digest in hexadecimal. */
@Component
public class SecureRandomTokenGenerator implements SecureTokenGenerator {
  private static final int TOKEN_BYTES = 32;
  private final SecureRandom random = new SecureRandom();

  @Override
  public String generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  @Override
  public String digest(String rawToken) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is not available", impossible);
    }
  }
}
