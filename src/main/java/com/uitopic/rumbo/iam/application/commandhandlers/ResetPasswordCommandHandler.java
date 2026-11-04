package com.uitopic.rumbo.iam.application.commandhandlers;

import com.uitopic.rumbo.iam.application.ports.IamEventPublisher;
import com.uitopic.rumbo.iam.application.ports.PasswordHasher;
import com.uitopic.rumbo.iam.application.ports.PasswordResetTokenStore;
import com.uitopic.rumbo.iam.application.ports.SecureTokenGenerator;
import com.uitopic.rumbo.iam.application.services.SessionRevocationService;
import com.uitopic.rumbo.iam.domain.exceptions.InvalidPasswordResetTokenException;
import com.uitopic.rumbo.iam.domain.exceptions.WeakPasswordException;
import com.uitopic.rumbo.iam.domain.model.aggregates.UserAccount;
import com.uitopic.rumbo.iam.domain.model.commands.ResetPasswordCommand;
import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordPolicy;
import com.uitopic.rumbo.iam.domain.repositories.AuthSessionRepository;
import com.uitopic.rumbo.iam.domain.repositories.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Sets a new password from a recovery link (US-28).
 * <ul>
 *   <li>The password policy is checked before the link is used, so a weak password does not waste it.</li>
 *   <li>The link works once; unknown, expired and used links all answer the same way.</li>
 *   <li>Every open session ends: whoever had the old password loses access at once.</li>
 * </ul>
 */
@Service
public class ResetPasswordCommandHandler {
  private final PasswordResetTokenStore resetTokens;
  private final SecureTokenGenerator tokenGenerator;
  private final UserAccountRepository accounts;
  private final AuthSessionRepository sessions;
  private final PasswordHasher passwordHasher;
  private final PasswordPolicy passwordPolicy;
  private final SessionRevocationService revocation;
  private final IamEventPublisher events;
  private final Clock clock;

  public ResetPasswordCommandHandler(PasswordResetTokenStore resetTokens, SecureTokenGenerator tokenGenerator,
                                     UserAccountRepository accounts, AuthSessionRepository sessions,
                                     PasswordHasher passwordHasher, PasswordPolicy passwordPolicy,
                                     SessionRevocationService revocation, IamEventPublisher events, Clock clock) {
    this.resetTokens = resetTokens;
    this.tokenGenerator = tokenGenerator;
    this.accounts = accounts;
    this.sessions = sessions;
    this.passwordHasher = passwordHasher;
    this.passwordPolicy = passwordPolicy;
    this.revocation = revocation;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  public void handle(ResetPasswordCommand command) {
    if (command.resetToken() == null || command.resetToken().isBlank()) throw new InvalidPasswordResetTokenException();
    if (!passwordPolicy.isSatisfiedBy(command.newPassword())) {
      throw new WeakPasswordException(passwordPolicy.description());
    }
    UserAccount account = resetTokens.consume(tokenGenerator.digest(command.resetToken()))
      .flatMap(accounts::findById)
      .filter(UserAccount::canRecoverPassword)
      .orElseThrow(InvalidPasswordResetTokenException::new);
    Instant now = clock.instant();
    account.changePassword(passwordHasher.hash(command.newPassword()), now);
    accounts.save(account);
    revocation.propagate(sessions.revokeAllByUserId(account.id(), now));
    events.publish(account.pullDomainEvents());
  }
}
