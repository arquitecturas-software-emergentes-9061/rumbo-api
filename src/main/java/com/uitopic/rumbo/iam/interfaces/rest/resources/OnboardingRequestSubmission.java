package com.uitopic.rumbo.iam.interfaces.rest.resources;

import com.uitopic.rumbo.iam.domain.model.valueobjects.ClinicSitesRange;
import com.uitopic.rumbo.iam.domain.model.valueobjects.OnboardingRequestType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/onboarding-requests}, sent by the contact form of the landing page.
 *
 * @param sitesRange optional size of the clinic network
 * @param message    optional free text
 */
public record OnboardingRequestSubmission(
  @NotNull OnboardingRequestType type,
  @NotBlank @Size(max = 120) String fullName,
  @NotBlank @Email @Size(max = 254) String email,
  @NotBlank @Size(max = 120) String clinicName,
  @NotBlank @Size(max = 120) String role,
  ClinicSitesRange sitesRange,
  @Size(max = 1000) String message,
  @AssertTrue(message = "The privacy policy must be accepted") boolean privacyConsent) {
}
