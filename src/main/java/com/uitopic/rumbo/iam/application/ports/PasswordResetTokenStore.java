package com.uitopic.rumbo.iam.application.ports;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port to the store of recovery links (US-28). It keeps only the digest of each token, lets every token
 * expire on its own and allows one open link per account.
 */
public interface PasswordResetTokenStore {
  /** Stores the link of the account for {@code ttl}; an earlier link of the same account stops working. */
  void save(UUID userId, String tokenDigest, Duration ttl);

  /**
   * Returns the account of the link and deletes it in the same step, so a link works once even if it is opened twice
   * at the same time. Empty when the link is unknown, expired or already used.
   */
  Optional<UUID> consume(String tokenDigest);

  /** The store cannot answer; the caller decides how to degrade. */
  class Unavailable extends RuntimeException {
    public Unavailable(Throwable cause) {
      super("Password reset token store is unavailable", cause);
    }
  }
}
