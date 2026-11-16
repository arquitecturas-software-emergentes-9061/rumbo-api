package com.uitopic.rumbo.iam.domain.model.commands;

import com.uitopic.rumbo.iam.domain.model.valueobjects.UserRole;

/**
 * Request to register an account.
 *
 * @param email email typed by the user
 * @param password plain password; it is checked against the policy and hashed, never stored
 * @param role {@code CLINIC_ADMIN} from the web app or {@code PATIENT} from the mobile app
 * @param fullName name forwarded to Profiles and Preferences
 * @param clinicName clinic forwarded to Indoor Spaces Planning; required for administrators
 */
public record SignUpCommand(String email, String password, UserRole role, String fullName, String clinicName) {
  @Override
  public String toString() {
    return "SignUpCommand[email=" + email + ", role=" + role + ", clinicName=" + clinicName + "]";
  }
}
