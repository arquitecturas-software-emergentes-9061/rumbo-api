package com.uitopic.rumbo.communications.application.eventhandlers;

import com.uitopic.rumbo.communications.application.ports.PasswordRecoveryDelivery;
import com.uitopic.rumbo.iam.domain.model.events.PasswordResetRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** IAM owns tokens and their lifetime; Communications delivers the committed event. */
@Component
@ConditionalOnProperty(name = "rumbo.communications.recovery-mail.enabled", havingValue = "true")
public class PasswordRecoveryEmailHandler {
  private static final Logger LOG = LoggerFactory.getLogger(PasswordRecoveryEmailHandler.class);
  private final PasswordRecoveryDelivery delivery;

  public PasswordRecoveryEmailHandler(PasswordRecoveryDelivery delivery) { this.delivery = delivery; }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(PasswordResetRequestedEvent event) {
    try {
      delivery.send(event.email(), event.resetToken(), event.expiresAt());
    } catch (PasswordRecoveryDelivery.Unavailable unavailable) {
      // No SMTP exception: providers may include the recipient or message body in their diagnostics.
      // Keep the same HTTP 202 as an unknown email. The user can request a fresh link after recovery.
      LOG.error("Password recovery email could not be delivered for account {}", event.userId());
    }
  }
}
