package com.uitopic.rumbo.communications.application.ports;

import java.util.Optional;
import java.util.UUID;

/** Where and whether an account can be written to. Communications reads it from IAM without sharing its model. */
public interface RecipientDirectory {
  /** Contact of the account, empty if it does not exist. */
  Optional<Contact> contactOf(UUID userId);

  /**
   * @param role {@code CLINIC_ADMIN} or {@code PATIENT}
   * @param reachable {@code false} when the account is disabled and must not receive messages
   */
  record Contact(UUID userId, String email, String role, boolean reachable) {
    public boolean isAdministrator() { return "CLINIC_ADMIN".equals(role); }
  }
}
