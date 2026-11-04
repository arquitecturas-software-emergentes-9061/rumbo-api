package com.uitopic.rumbo.iam.interfaces.rest.resources;

import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code PATCH /api/v1/user-accounts/me/password}. */
public record ChangePasswordRequest(
  @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String currentPassword,
  @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String newPassword) {
  @Override
  public String toString() {
    return "ChangePasswordRequest[***]";
  }
}
