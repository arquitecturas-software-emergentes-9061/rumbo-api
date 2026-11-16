package com.uitopic.rumbo.iam.infrastructure.persistence.jpa.adapters;

import com.uitopic.rumbo.iam.domain.exceptions.EmailAlreadyRegisteredException;
import com.uitopic.rumbo.iam.domain.model.aggregates.UserAccount;
import com.uitopic.rumbo.iam.domain.model.valueobjects.EmailAddress;
import com.uitopic.rumbo.iam.domain.repositories.UserAccountRepository;
import com.uitopic.rumbo.iam.infrastructure.persistence.jpa.mappers.UserAccountJpaMapper;
import com.uitopic.rumbo.iam.infrastructure.persistence.jpa.repositories.UserAccountJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL adapter of {@link UserAccountRepository}. It flushes on save so a concurrent sign-up with the same email
 * fails here, on the unique index, and surfaces as the domain error instead of a generic database error at commit.
 */
@Repository
public class UserAccountRepositoryImpl implements UserAccountRepository {
  private final UserAccountJpaRepository jpa;

  public UserAccountRepositoryImpl(UserAccountJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public UserAccount save(UserAccount account) {
    try {
      return UserAccountJpaMapper.toDomain(jpa.saveAndFlush(UserAccountJpaMapper.toEntity(account)));
    } catch (DataIntegrityViolationException duplicate) {
      throw new EmailAlreadyRegisteredException();
    }
  }

  @Override
  public Optional<UserAccount> findById(UUID id) {
    return jpa.findById(id).map(UserAccountJpaMapper::toDomain);
  }

  @Override
  public Optional<UserAccount> findByEmail(EmailAddress email) {
    return jpa.findByEmail(email.value()).map(UserAccountJpaMapper::toDomain);
  }

  @Override
  public boolean existsByEmail(EmailAddress email) {
    return jpa.existsByEmail(email.value());
  }
}
