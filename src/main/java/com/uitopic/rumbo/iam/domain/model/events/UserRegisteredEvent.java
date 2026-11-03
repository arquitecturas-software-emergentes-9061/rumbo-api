package com.uitopic.rumbo.iam.domain.model.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a new account is committed. It is the contract other bounded contexts consume:
 * <ul>
 *   <li>Profiles and Preferences creates the profile with {@code fullName} (TS-33).</li>
 *   <li>Indoor Spaces Planning creates the clinic named {@code clinicName} for a {@code CLINIC_ADMIN} and answers
 *   with {@code ClinicRegisteredEvent} so IAM can link the account to it.</li>
 *   <li>Communications sends the welcome notice.</li>
 * </ul>
 * Identity does not store {@code fullName} or {@code clinicName}; it only forwards them from the sign-up form.
 * Listeners should use {@code @TransactionalEventListener} and, if they write, their own transaction
 * ({@code Propagation.REQUIRES_NEW}).
 *
 * @param userId identifier of the new account
 * @param email normalized email of the account
 * @param role {@code CLINIC_ADMIN} or {@code PATIENT}
 * @param fullName name typed in the sign-up form
 * @param clinicName clinic typed by an administrator; {@code null} for patients
 * @param occurredAt instant of the registration
 */
public record UserRegisteredEvent(UUID userId, String email, String role, String fullName, String clinicName,
                                  Instant occurredAt) {
}
