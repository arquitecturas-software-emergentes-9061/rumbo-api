package com.uitopic.rumbo.acceptance.support;

import com.uitopic.rumbo.iam.domain.model.events.PasswordResetRequestedEvent;
import org.springframework.context.event.EventListener;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stands in for the inbox of the user in acceptance tests: it keeps the last recovery link sent to each email.
 * Sending the real email belongs to Communications, which does not exist yet.
 */
public class RecoveryMailbox {
  private final Map<String, String> lastLinkByEmail = new ConcurrentHashMap<>();

  @EventListener
  void receive(PasswordResetRequestedEvent event) {
    lastLinkByEmail.put(event.email(), event.resetToken());
  }

  public Optional<String> lastLinkSentTo(String email) {
    return Optional.ofNullable(lastLinkByEmail.get(email));
  }
}
