package com.uitopic.rumbo.iam.application.commandhandlers;

import com.uitopic.rumbo.iam.application.ports.IamEventPublisher;
import com.uitopic.rumbo.iam.application.ports.PasswordHasher;
import com.uitopic.rumbo.iam.application.results.AuthenticatedUser;
import com.uitopic.rumbo.iam.application.services.SessionCredentialsIssuer;
import com.uitopic.rumbo.iam.domain.exceptions.AccountDisabledException;
import com.uitopic.rumbo.iam.domain.exceptions.AccountLockedException;
import com.uitopic.rumbo.iam.domain.exceptions.InvalidCredentialsException;
import com.uitopic.rumbo.iam.domain.model.aggregates.UserAccount;
import com.uitopic.rumbo.iam.domain.model.commands.SignInCommand;
import com.uitopic.rumbo.iam.domain.model.valueobjects.EmailAddress;
import com.uitopic.rumbo.iam.domain.model.valueobjects.LockoutPolicy;
import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordHash;
import com.uitopic.rumbo.iam.domain.repositories.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Verifies credentials and opens a session (TS-01, TS-32 scenario 1).
 * <ul>
 *   <li>Unknown email and wrong password answer the same way, and an unknown email still runs one hash comparison
 *   so response time does not reveal which accounts exist.</li>
 *   <li>A locked account is rejected before its password is checked, so guessing cannot continue during the lock.</li>
 *   <li>Failed attempts are committed even though the use case ends with an error.</li>
 * </ul>
 */
@Service
public class SignInCommandHandler {
  private final UserAccountRepository accounts;
  private final PasswordHasher passwordHasher;
  private final LockoutPolicy lockoutPolicy;
  private final SessionCredentialsIssuer credentialsIssuer;
  private final IamEventPublisher events;
  private final Clock clock;
  private final PasswordHash unknownAccountHash;

  public SignInCommandHandler(UserAccountRepository accounts, PasswordHasher passwordHasher,
                              LockoutPolicy lockoutPolicy, SessionCredentialsIssuer credentialsIssuer,
                              IamEventPublisher events, Clock clock) {
    this.accounts = accounts;
    this.passwordHasher = passwordHasher;
    this.lockoutPolicy = lockoutPolicy;
    this.credentialsIssuer = credentialsIssuer;
    this.events = events;
    this.clock = clock;
    this.unknownAccountHash = passwordHasher.hash(UUID.randomUUID().toString());
  }

  @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
  public AuthenticatedUser handle(SignInCommand command) {
    Instant now = clock.instant();
    Optional<UserAccount> found = parseEmail(command.email()).flatMap(accounts::findByEmail);
    if (found.isEmpty()) {
      passwordHasher.matches(command.password(), unknownAccountHash);
      throw new InvalidCredentialsException();
    }
    UserAccount account = found.get();
    if (account.isLocked(now)) throw new AccountLockedException(account.lockedUntil().orElseThrow());
    if (!passwordHasher.matches(command.password(), account.passwordHash())) {
      account.registerFailedLogin(lockoutPolicy, now);
      accounts.save(account);
      events.publish(account.pullDomainEvents());
      if (account.isLocked(now)) throw new AccountLockedException(account.lockedUntil().orElseThrow());
      throw new InvalidCredentialsException();
    }
    if (account.isDisabled()) throw new AccountDisabledException();
    account.registerSuccessfulLogin(now);
    UserAccount saved = accounts.save(account);
    return credentialsIssuer.openSession(saved, now);
  }

  private static Optional<EmailAddress> parseEmail(String email) {
    try {
      return Optional.of(new EmailAddress(email));
    } catch (IllegalArgumentException invalidFormat) {
      return Optional.empty();
    }
  }
}
