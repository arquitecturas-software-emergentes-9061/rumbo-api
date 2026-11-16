package com.uitopic.rumbo.iam.infrastructure.persistence.jpa.entities;

import com.uitopic.rumbo.iam.domain.model.valueobjects.ClinicSitesRange;
import com.uitopic.rumbo.iam.domain.model.valueobjects.OnboardingRequestType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Row of {@code identity_onboarding_requests}: a demo or onboarding request left on the landing page. */
@Entity
@Table(name = "identity_onboarding_requests")
public class OnboardingRequestJpaEntity {
  @Id
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "request_type", nullable = false, length = 20)
  private OnboardingRequestType type;

  @Column(name = "requester_name", nullable = false, length = 120)
  private String requesterName;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "clinic_name", nullable = false, length = 120)
  private String clinicName;

  @Column(name = "requester_role", nullable = false, length = 120)
  private String requesterRole;

  @Enumerated(EnumType.STRING)
  @Column(name = "sites_range", length = 20)
  private ClinicSitesRange sitesRange;

  @Column(length = 1000)
  private String message;

  @Column(name = "consent_given_at", nullable = false, updatable = false)
  private Instant consentGivenAt;

  @Column(name = "submitted_at", nullable = false, updatable = false)
  private Instant submittedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public OnboardingRequestType getType() { return type; }
  public void setType(OnboardingRequestType type) { this.type = type; }
  public String getRequesterName() { return requesterName; }
  public void setRequesterName(String requesterName) { this.requesterName = requesterName; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getClinicName() { return clinicName; }
  public void setClinicName(String clinicName) { this.clinicName = clinicName; }
  public String getRequesterRole() { return requesterRole; }
  public void setRequesterRole(String requesterRole) { this.requesterRole = requesterRole; }
  public ClinicSitesRange getSitesRange() { return sitesRange; }
  public void setSitesRange(ClinicSitesRange sitesRange) { this.sitesRange = sitesRange; }
  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }
  public Instant getConsentGivenAt() { return consentGivenAt; }
  public void setConsentGivenAt(Instant consentGivenAt) { this.consentGivenAt = consentGivenAt; }
  public Instant getSubmittedAt() { return submittedAt; }
  public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
}
