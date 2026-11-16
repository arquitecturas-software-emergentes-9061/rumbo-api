package com.uitopic.rumbo.iam.application.ports;

import java.time.Duration;
import java.util.UUID;

/**
 * Outbound port to a fast store of revoked sessions, checked on every authenticated request. An entry only needs to
 * live as long as an access token can: after that the token is rejected for being expired anyway.
 */
public interface RevokedSessionCache {
  void markRevoked(UUID sessionId, Duration ttl);

  /** @throws Unavailable when the store cannot answer; the caller decides how to degrade */
  boolean isRevoked(UUID sessionId);

  class Unavailable extends RuntimeException {
    public Unavailable(Throwable cause) {
      super("Revoked session cache is unavailable", cause);
    }
  }
}
