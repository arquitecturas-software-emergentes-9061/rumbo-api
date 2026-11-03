package com.uitopic.rumbo.iam.infrastructure.persistence.jpa.mappers;

import com.uitopic.rumbo.iam.domain.model.aggregates.UserAccount;
import com.uitopic.rumbo.iam.domain.model.valueobjects.EmailAddress;
import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordHash;
import com.uitopic.rumbo.iam.infrastructure.persistence.jpa.entities.UserAccountJpaEntity;

/** Converts between the {@link UserAccount} aggregate and its persistence entity. */
public final class UserAccountJpaMapper {
  private UserAccountJpaMapper() {}

  public static UserAccountJpaEntity toEntity(UserAccount account) {
    UserAccountJpaEntity entity = new UserAccountJpaEntity();
    entity.setId(account.id());
    entity.setEmail(account.email().value());
    entity.setPasswordHash(account.passwordHash().value());
    entity.setRole(account.role());
    entity.setStatus(account.status());
    entity.setFailedLoginAttempts(account.failedLoginAttempts());
    entity.setLockedUntil(account.lockedUntil().orElse(null));
    entity.setClinicId(account.clinicId().orElse(null));
    entity.setCreatedAt(account.createdAt());
    return entity;
  }

  public static UserAccount toDomain(UserAccountJpaEntity entity) {
    return UserAccount.restore(entity.getId(), new EmailAddress(entity.getEmail()),
      new PasswordHash(entity.getPasswordHash()), entity.getRole(), entity.getStatus(),
      entity.getFailedLoginAttempts(), entity.getLockedUntil(), entity.getClinicId(), entity.getCreatedAt(),
      entity.getUpdatedAt());
  }
}
