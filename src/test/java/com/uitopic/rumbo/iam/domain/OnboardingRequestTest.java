package com.uitopic.rumbo.iam.domain;

import com.uitopic.rumbo.iam.domain.exceptions.PrivacyConsentRequiredException;
import com.uitopic.rumbo.iam.domain.model.aggregates.OnboardingRequest;
import com.uitopic.rumbo.iam.domain.model.valueobjects.ClinicSitesRange;
import com.uitopic.rumbo.iam.domain.model.valueobjects.EmailAddress;
import com.uitopic.rumbo.iam.domain.model.valueobjects.OnboardingRequestType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PR-4: a landing request is only kept with consent, and its texts are stored clean. */
class OnboardingRequestTest {
  private static final Instant NOW = Instant.parse("2026-11-15T15:00:00Z");

  @Test
  void aRequestWithConsentKeepsTrimmedTextsAndTheMomentOfConsent() {
    OnboardingRequest request = OnboardingRequest.submit(UUID.randomUUID(), OnboardingRequestType.DEMO,
      "  Carmen Mendoza ", new EmailAddress(" Carmen@Clinica.PE "), " Clinica San Lucas", "Directora de TI ",
      ClinicSitesRange.TWO_TO_THREE, "  ", true, NOW);

    assertThat(request.requesterName()).isEqualTo("Carmen Mendoza");
    assertThat(request.email().value()).isEqualTo("carmen@clinica.pe");
    assertThat(request.clinicName()).isEqualTo("Clinica San Lucas");
    assertThat(request.requesterRole()).isEqualTo("Directora de TI");
    assertThat(request.message()).isNull();
    assertThat(request.consentGivenAt()).isEqualTo(NOW);
    assertThat(request.submittedAt()).isEqualTo(NOW);
  }

  @Test
  void withoutConsentNothingIsKept() {
    assertThatThrownBy(() -> submit("Carmen", "Clinica San Lucas", null, false))
      .isInstanceOf(PrivacyConsentRequiredException.class)
      .extracting("code").isEqualTo("PRIVACY_CONSENT_REQUIRED");
  }

  @Test
  void requiredTextsCannotBeBlankOrTooLong() {
    assertThatThrownBy(() -> submit(" ", "Clinica San Lucas", null, true))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Requester name");
    assertThatThrownBy(() -> submit("Carmen", "x".repeat(OnboardingRequest.MAX_TEXT_LENGTH + 1), null, true))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Clinic name");
    assertThatThrownBy(() -> submit("Carmen", "Clinica San Lucas",
      "x".repeat(OnboardingRequest.MAX_MESSAGE_LENGTH + 1), true))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Message");
  }

  @Test
  void theTypeIsRequired() {
    assertThatThrownBy(() -> OnboardingRequest.submit(UUID.randomUUID(), null, "Carmen",
      new EmailAddress("carmen@clinica.pe"), "Clinica San Lucas", "TI", null, null, true, NOW))
      .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("type");
  }

  private static OnboardingRequest submit(String name, String clinic, String message, boolean consent) {
    return OnboardingRequest.submit(UUID.randomUUID(), OnboardingRequestType.ONBOARDING, name,
      new EmailAddress("carmen@clinica.pe"), clinic, "Directora de TI", ClinicSitesRange.ONE, message, consent, NOW);
  }
}
