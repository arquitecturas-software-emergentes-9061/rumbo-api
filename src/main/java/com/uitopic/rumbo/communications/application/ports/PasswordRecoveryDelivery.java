package com.uitopic.rumbo.communications.application.ports;

import java.time.Instant;

/** Private delivery channel: secrets must never enter the in-app notification history. */
public interface PasswordRecoveryDelivery {
  void send(String recipient, String token, Instant expiresAt);

  /** Contains no recipient, token, or transport exception that could disclose the message. */
  final class Unavailable extends RuntimeException {
    public Unavailable() { super("Password recovery delivery unavailable"); }
  }
}
