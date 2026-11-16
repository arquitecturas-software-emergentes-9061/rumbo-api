package com.uitopic.rumbo.iam.interfaces.rest.resources;

import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/password-recovery/confirmations}: the secret of the link and the new password. */
public record PasswordResetConfirmationRequest(
  @NotBlank @Size(max = 128) String resetToken,
  @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) @Schema(example = "Rumbo2027") String newPassword) {
  @Override
  public String toString() {
    return "PasswordResetConfirmationRequest[***]";
  }
}
