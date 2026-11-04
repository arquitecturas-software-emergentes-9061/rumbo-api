package com.uitopic.rumbo.iam.application.ports;

import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordHash;

/** Outbound port to hash and verify passwords without depending on a cryptographic library. */
public interface PasswordHasher {
  PasswordHash hash(String rawPassword);

  boolean matches(String rawPassword, PasswordHash hash);
}
