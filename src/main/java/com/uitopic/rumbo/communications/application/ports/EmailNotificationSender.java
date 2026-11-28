package com.uitopic.rumbo.communications.application.ports;

/** Outbound email of the notifications on the {@code EMAIL} channel. Present only when email delivery is enabled. */
public interface EmailNotificationSender {
  /**
   * Hands the message to the mail server.
   *
   * @throws DeliveryFailure if it was not accepted; {@link DeliveryFailure#temporary()} says whether to retry
   */
  void send(String to, String subject, String body);

  /** The mail server did not accept the message. */
  class DeliveryFailure extends RuntimeException {
    private final boolean temporary;

    public DeliveryFailure(String message, boolean temporary, Throwable cause) {
      super(message, cause);
      this.temporary = temporary;
    }

    /** {@code true} when the same message may be accepted later, such as a server that is down. */
    public boolean temporary() { return temporary; }
  }
}
