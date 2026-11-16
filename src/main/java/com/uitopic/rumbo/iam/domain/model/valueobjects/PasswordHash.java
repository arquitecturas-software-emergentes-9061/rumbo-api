package com.uitopic.rumbo.iam.domain.model.valueobjects;

/**
 * Irreversible digest of a password. It never holds the plain password, and {@link #toString()} hides the digest
 * so it cannot leak through logs.
 *
 * @param value digest produced by the configured password hasher
 */
public record PasswordHash(String value) {
  public PasswordHash {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Password hash is required");
  }

  @Override
  public String toString() { return "PasswordHash[****]"; }
}
