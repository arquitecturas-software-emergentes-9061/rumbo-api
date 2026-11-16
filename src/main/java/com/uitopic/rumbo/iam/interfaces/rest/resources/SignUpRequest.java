package com.uitopic.rumbo.iam.interfaces.rest.resources;

import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordPolicy;
import com.uitopic.rumbo.iam.domain.model.valueobjects.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/auth/sign-up} (MU-02). {@code clinicName} is required for {@code CLINIC_ADMIN} and
 * ignored for {@code PATIENT}.
 */
public record SignUpRequest(
  @NotBlank @Email @Size(max = 254) String email,
  @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) @Schema(example = "Rumbo2026") String password,
  @NotNull UserRole role,
  @NotBlank @Size(max = 120) String fullName,
  @Size(max = 120) String clinicName,
  @AssertTrue(message = "must be accepted") boolean acceptedTerms) {
  @Override
  public String toString() {
    return "SignUpRequest[email=" + email + ", role=" + role + "]";
  }
}
