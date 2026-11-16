package com.uitopic.rumbo.iam.interfaces.acl;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Stable read-only IAM contract for other bounded contexts. Communications uses it to know where to write to a user
 * without depending on IAM aggregates or tables.
 */
public interface IamContextFacade {
  /** Contact data of an account, empty if the account does not exist. */
  Optional<UserContactData> getUserContactById(UUID userId);

  /**
   * Contact data exported by IAM.
   *
   * @param userId account identifier
   * @param email address to write to
   * @param role {@code CLINIC_ADMIN} or {@code PATIENT}
   * @param reachable {@code false} when the account is disabled and should not receive messages
   */
  record UserContactData(UUID userId, String email, String role, boolean reachable) {
    public UserContactData {
      Objects.requireNonNull(userId, "userId is required");
      Objects.requireNonNull(email, "email is required");
      Objects.requireNonNull(role, "role is required");
    }
  }
}
