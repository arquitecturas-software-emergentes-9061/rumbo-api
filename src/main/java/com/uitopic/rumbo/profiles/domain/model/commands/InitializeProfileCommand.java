package com.uitopic.rumbo.profiles.domain.model.commands;

import java.util.UUID;

/**
 * Asks to create the initial profile of a newly registered account.
 *
 * @param userId identifier of the IAM account
 * @param fullName name typed in the sign-up form
 * @param role {@code CLINIC_ADMIN} or {@code PATIENT}
 */
public record InitializeProfileCommand(UUID userId, String fullName, String role) {
}
