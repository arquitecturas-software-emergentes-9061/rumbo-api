package com.uitopic.rumbo.iam.application.commandhandlers;

import com.uitopic.rumbo.iam.application.ports.IamEventPublisher;
import com.uitopic.rumbo.iam.application.ports.PasswordHasher;
import com.uitopic.rumbo.iam.application.services.SessionRevocationService;
import com.uitopic.rumbo.iam.domain.exceptions.IncorrectCurrentPasswordException;
import com.uitopic.rumbo.iam.domain.exceptions.UserAccountNotFoundException;
import com.uitopic.rumbo.iam.domain.exceptions.WeakPasswordException;
import com.uitopic.rumbo.iam.domain.model.aggregates.UserAccount;
import com.uitopic.rumbo.iam.domain.model.commands.ChangePasswordCommand;
import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordPolicy;
import com.uitopic.rumbo.iam.domain.repositories.AuthSessionRepository;
import com.uitopic.rumbo.iam.domain.repositories.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Changes the password of a signed-in account. The current password is required, so a borrowed unlocked device is
 * not enough; the session that asks stays open and every other session ends.
 */
@Service
public class ChangePasswordCommandHandler {
  private final UserAccountRepository accounts;
  private final AuthSessionRepository sessions;
  private final PasswordHasher passwordHasher;
  private final PasswordPolicy passwordPolicy;
  private final SessionRevocationService revocation;
  private final IamEventPublisher events;
  private final Clock clock;

  public ChangePasswordCommandHandler(UserAccountRepository accounts, AuthSessionRepository sessions,
                                      PasswordHasher passwordHasher, PasswordPolicy passwordPolicy,
                                      SessionRevocationService revocation, IamEventPublisher events, Clock clock) {
    this.accounts = accounts;
    this.sessions = sessions;
    this.passwordHasher = passwordHasher;
    this.passwordPolicy = passwordPolicy;
    this.revocation = revocation;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  public void handle(ChangePasswordCommand command) {
    UserAccount account = accounts.findById(command.userId())
      .orElseThrow(() -> new UserAccountNotFoundException(command.userId()));
    if (command.currentPassword() == null || !passwordHasher.matches(command.currentPassword(),
      account.passwordHash())) {
      throw new IncorrectCurrentPasswordException();
    }
    if (!passwordPolicy.isSatisfiedBy(command.newPassword())) {
      throw new WeakPasswordException(passwordPolicy.description());
    }
    Instant now = clock.instant();
    account.changePassword(passwordHasher.hash(command.newPassword()), now);
    accounts.save(account);
    revocation.propagate(sessions.revokeAllByUserIdExcept(account.id(), command.sessionId(), now));
    events.publish(account.pullDomainEvents());
  }
}
