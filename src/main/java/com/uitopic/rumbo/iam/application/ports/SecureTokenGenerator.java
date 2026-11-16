package com.uitopic.rumbo.iam.application.ports;

/**
 * Outbound port for opaque secrets such as refresh tokens and password-reset tokens.
 * Only {@link #digest(String)} is ever stored; the raw value is handed once to the user.
 */
public interface SecureTokenGenerator {
  /** New unguessable value (at least 256 bits of entropy), safe to place in URLs. */
  String generate();

  /** Deterministic one-way digest used to store and look up a token. */
  String digest(String rawToken);
}
