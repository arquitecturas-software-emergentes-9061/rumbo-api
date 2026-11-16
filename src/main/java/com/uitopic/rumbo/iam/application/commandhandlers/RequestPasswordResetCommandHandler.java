package com.uitopic.rumbo.iam.application.commandhandlers;

import com.uitopic.rumbo.iam.application.ports.IamEventPublisher;
import com.uitopic.rumbo.iam.application.ports.PasswordResetTokenStore;
import com.uitopic.rumbo.iam.application.ports.SecureTokenGenerator;
import com.uitopic.rumbo.iam.domain.model.aggregates.UserAccount;
import com.uitopic.rumbo.iam.domain.model.commands.RequestPasswordResetCommand;
import com.uitopic.rumbo.iam.domain.model.valueobjects.EmailAddress;
import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordRecoveryPolicy;
import com.uitopic.rumbo.iam.domain.repositories.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Issues a recovery link (US-28). The caller always gets the same neutral answer: an unknown email, a disabled
 * account and a store failure end silently, so the endpoint cannot be used to find out which accounts exist.
 * The email itself is sent by Communications when it receives {@code PasswordResetRequestedEvent}.
 */
@Service
public class RequestPasswordResetCommandHandler {
  private static final Logger log = LoggerFactory.getLogger(RequestPasswordResetCommandHandler.class);
  private final UserAccountRepository accounts;
  private final SecureTokenGenerator tokenGenerator;
  private final PasswordResetTokenStore resetTokens;
  private final PasswordRecoveryPolicy recoveryPolicy;
  private final IamEventPublisher events;
  private final Clock clock;

  public RequestPasswordResetCommandHandler(UserAccountRepository accounts, SecureTokenGenerator tokenGenerator,
                                            PasswordResetTokenStore resetTokens, PasswordRecoveryPolicy recoveryPolicy,
                                            IamEventPublisher events, Clock clock) {
    this.accounts = accounts;
    this.tokenGenerator = tokenGenerator;
    this.resetTokens = resetTokens;
    this.recoveryPolicy = recoveryPolicy;
    this.events = events;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public void handle(RequestPasswordResetCommand command) {
    Optional<UserAccount> found = parseEmail(command.email()).flatMap(accounts::findByEmail)
      .filter(UserAccount::canRecoverPassword);
    if (found.isEmpty()) return;
    UserAccount account = found.get();
    Instant now = clock.instant();
    String resetToken = tokenGenerator.generate();
    try {
      resetTokens.save(account.id(), tokenGenerator.digest(resetToken), recoveryPolicy.tokenLifetime());
    } catch (PasswordResetTokenStore.Unavailable unavailable) {
      log.error("Recovery link for account {} not issued: the token store is unavailable", account.id(), unavailable);
      return;
    }
    account.requestPasswordReset(resetToken, recoveryPolicy.expiresAt(now), now);
    events.publish(account.pullDomainEvents());
  }

  private static Optional<EmailAddress> parseEmail(String email) {
    try {
      return Optional.of(new EmailAddress(email));
    } catch (IllegalArgumentException invalidFormat) {
      return Optional.empty();
    }
  }
}
