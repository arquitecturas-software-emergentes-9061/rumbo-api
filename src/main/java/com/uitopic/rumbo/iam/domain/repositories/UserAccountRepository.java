package com.uitopic.rumbo.iam.domain.repositories;

import com.uitopic.rumbo.iam.domain.model.aggregates.UserAccount;
import com.uitopic.rumbo.iam.domain.model.valueobjects.EmailAddress;

import java.util.Optional;
import java.util.UUID;

/** Persistence contract of {@link UserAccount}, independent of the storage technology. */
public interface UserAccountRepository {
  /**
   * Stores the account and returns it as persisted.
   *
   * @throws com.uitopic.rumbo.iam.domain.exceptions.EmailAlreadyRegisteredException if another account already uses the email
   */
  UserAccount save(UserAccount account);

  Optional<UserAccount> findById(UUID id);

  Optional<UserAccount> findByEmail(EmailAddress email);

  boolean existsByEmail(EmailAddress email);
}
