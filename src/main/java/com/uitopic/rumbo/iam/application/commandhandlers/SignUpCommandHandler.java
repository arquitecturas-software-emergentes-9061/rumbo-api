package com.uitopic.rumbo.iam.application.commandhandlers;

import com.uitopic.rumbo.iam.application.ports.IamEventPublisher;
import com.uitopic.rumbo.iam.application.ports.PasswordHasher;
import com.uitopic.rumbo.iam.domain.exceptions.EmailAlreadyRegisteredException;
import com.uitopic.rumbo.iam.domain.exceptions.WeakPasswordException;
import com.uitopic.rumbo.iam.domain.model.aggregates.UserAccount;
import com.uitopic.rumbo.iam.domain.model.commands.SignUpCommand;
import com.uitopic.rumbo.iam.domain.model.valueobjects.EmailAddress;
import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordPolicy;
import com.uitopic.rumbo.iam.domain.repositories.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Registers an account (TS-26): checks email uniqueness and the password policy, hashes the password, stores the
 * account and publishes {@code UserRegisteredEvent}.
 */
@Service
public class SignUpCommandHandler {
  private final UserAccountRepository accounts;
  private final PasswordHasher passwordHasher;
  private final PasswordPolicy passwordPolicy;
  private final IamEventPublisher events;
  private final Clock clock;

  public SignUpCommandHandler(UserAccountRepository accounts, PasswordHasher passwordHasher,
                              PasswordPolicy passwordPolicy, IamEventPublisher events, Clock clock) {
    this.accounts = accounts;
    this.passwordHasher = passwordHasher;
    this.passwordPolicy = passwordPolicy;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  public UserAccount handle(SignUpCommand command) {
    EmailAddress email = new EmailAddress(command.email());
    if (!passwordPolicy.isSatisfiedBy(command.password())) {
      throw new WeakPasswordException(passwordPolicy.description());
    }
    if (accounts.existsByEmail(email)) throw new EmailAlreadyRegisteredException();
    Instant now = clock.instant();
    UserAccount account = UserAccount.register(UUID.randomUUID(), email, passwordHasher.hash(command.password()),
      command.role(), command.fullName(), command.clinicName(), now);
    UserAccount saved = accounts.save(account);
    events.publish(account.pullDomainEvents());
    return saved;
  }
}
