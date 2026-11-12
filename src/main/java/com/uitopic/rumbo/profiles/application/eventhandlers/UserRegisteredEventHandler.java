package com.uitopic.rumbo.profiles.application.eventhandlers;

import com.uitopic.rumbo.iam.domain.model.events.UserRegisteredEvent;
import com.uitopic.rumbo.profiles.application.commandhandlers.InitializeProfileCommandHandler;
import com.uitopic.rumbo.profiles.domain.model.commands.InitializeProfileCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Creates the profile of every new account (TS-33) once IAM has committed the registration.
 * <p>
 * It runs only after the commit, so a rolled back sign-up never gets a profile. It opens no transaction of its own:
 * {@link InitializeProfileCommandHandler} writes in a separate one ({@code REQUIRES_NEW}), and any failure there,
 * including one raised while committing, is logged here and never reaches the already registered account.
 */
@Component
public class UserRegisteredEventHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(UserRegisteredEventHandler.class);

  private final InitializeProfileCommandHandler commands;

  public UserRegisteredEventHandler(InitializeProfileCommandHandler commands) {
    this.commands = commands;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void on(UserRegisteredEvent event) {
    try {
      commands.handle(new InitializeProfileCommand(event.userId(), event.fullName(), event.role()));
    } catch (RuntimeException failure) {
      LOGGER.error("Could not initialize profile for user {}", event.userId(), failure);
    }
  }
}
