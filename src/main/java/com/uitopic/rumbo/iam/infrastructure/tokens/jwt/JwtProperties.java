package com.uitopic.rumbo.iam.infrastructure.tokens.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

/**
 * Settings of the access-token signer. The secret comes from {@code JWT_SECRET} ({@code .env} locally, an app setting
 * in Azure); the application refuses to start without one long enough for HS256.
 */
@ConfigurationProperties(prefix = "rumbo.security.jwt")
public record JwtProperties(String secret, String issuer) {
  static final int MIN_SECRET_BYTES = 32;

  public JwtProperties {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalStateException("rumbo.security.jwt.secret (JWT_SECRET) must have at least "
        + MIN_SECRET_BYTES + " bytes");
    }
    if (issuer == null || issuer.isBlank()) issuer = "rumbo-api";
  }

  @Override
  public String toString() {
    return "JwtProperties[issuer=" + issuer + "]";
  }
}
