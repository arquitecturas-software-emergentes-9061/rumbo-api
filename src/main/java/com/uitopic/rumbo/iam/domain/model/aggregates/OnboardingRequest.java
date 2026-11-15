package com.uitopic.rumbo.iam.domain.model.aggregates;

import com.uitopic.rumbo.iam.domain.exceptions.PrivacyConsentRequiredException;
import com.uitopic.rumbo.iam.domain.model.valueobjects.ClinicSitesRange;
import com.uitopic.rumbo.iam.domain.model.valueobjects.EmailAddress;
import com.uitopic.rumbo.iam.domain.model.valueobjects.OnboardingRequestType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Request left on the landing page by a clinic that wants a demo or to start using Rumbo (PR-4). It is the first
 * contact with a future account, which is why it lives in IAM: sales reads it before the clinic signs up.
 * It can only exist with the privacy consent of the requester, and its texts are stored trimmed.
 */
public class OnboardingRequest {
  public static final int MAX_TEXT_LENGTH = 120;
  public static final int MAX_MESSAGE_LENGTH = 1000;

  private final UUID id;
  private final OnboardingRequestType type;
  private final String requesterName;
  private final EmailAddress email;
  private final String clinicName;
  private final String requesterRole;
  private final ClinicSitesRange sitesRange;
  private final String message;
  private final Instant consentGivenAt;
  private final Instant submittedAt;

  private OnboardingRequest(UUID id, OnboardingRequestType type, String requesterName, EmailAddress email,
                            String clinicName, String requesterRole, ClinicSitesRange sitesRange, String message,
                            Instant consentGivenAt, Instant submittedAt) {
    this.id = Objects.requireNonNull(id, "id");
    if (type == null) throw new IllegalArgumentException("Request type is required");
    this.type = type;
    this.requesterName = requiredText(requesterName, "Requester name");
    this.email = Objects.requireNonNull(email, "email");
    this.clinicName = requiredText(clinicName, "Clinic name");
    this.requesterRole = requiredText(requesterRole, "Requester role");
    this.sitesRange = sitesRange;
    this.message = optionalMessage(message);
    this.consentGivenAt = Objects.requireNonNull(consentGivenAt, "consentGivenAt");
    this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
  }

  /** Records a new request; without consent nothing is kept. */
  public static OnboardingRequest submit(UUID id, OnboardingRequestType type, String requesterName,
                                         EmailAddress email, String clinicName, String requesterRole,
                                         ClinicSitesRange sitesRange, String message, boolean privacyConsent,
                                         Instant now) {
    if (!privacyConsent) throw new PrivacyConsentRequiredException();
    return new OnboardingRequest(id, type, requesterName, email, clinicName, requesterRole, sitesRange, message,
      now, now);
  }

  /** Rebuilds a stored request. */
  public static OnboardingRequest restore(UUID id, OnboardingRequestType type, String requesterName,
                                          EmailAddress email, String clinicName, String requesterRole,
                                          ClinicSitesRange sitesRange, String message, Instant consentGivenAt,
                                          Instant submittedAt) {
    return new OnboardingRequest(id, type, requesterName, email, clinicName, requesterRole, sitesRange, message,
      consentGivenAt, submittedAt);
  }

  private static String requiredText(String value, String field) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    String trimmed = value.trim();
    if (trimmed.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException(field + " must have at most " + MAX_TEXT_LENGTH + " characters");
    }
    return trimmed;
  }

  private static String optionalMessage(String value) {
    if (value == null || value.isBlank()) return null;
    String trimmed = value.trim();
    if (trimmed.length() > MAX_MESSAGE_LENGTH) {
      throw new IllegalArgumentException("Message must have at most " + MAX_MESSAGE_LENGTH + " characters");
    }
    return trimmed;
  }

  public UUID id() { return id; }
  public OnboardingRequestType type() { return type; }
  public String requesterName() { return requesterName; }
  public EmailAddress email() { return email; }
  public String clinicName() { return clinicName; }
  public String requesterRole() { return requesterRole; }
  public ClinicSitesRange sitesRange() { return sitesRange; }
  public String message() { return message; }
  public Instant consentGivenAt() { return consentGivenAt; }
  public Instant submittedAt() { return submittedAt; }
}
