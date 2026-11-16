package com.uitopic.rumbo.iam.infrastructure.persistence.jpa.mappers;

import com.uitopic.rumbo.iam.domain.model.aggregates.OnboardingRequest;
import com.uitopic.rumbo.iam.domain.model.valueobjects.EmailAddress;
import com.uitopic.rumbo.iam.infrastructure.persistence.jpa.entities.OnboardingRequestJpaEntity;

/** Converts between {@link OnboardingRequest} and its row. */
public final class OnboardingRequestJpaMapper {
  private OnboardingRequestJpaMapper() {
  }

  public static OnboardingRequestJpaEntity toEntity(OnboardingRequest request) {
    OnboardingRequestJpaEntity entity = new OnboardingRequestJpaEntity();
    entity.setId(request.id());
    entity.setType(request.type());
    entity.setRequesterName(request.requesterName());
    entity.setEmail(request.email().value());
    entity.setClinicName(request.clinicName());
    entity.setRequesterRole(request.requesterRole());
    entity.setSitesRange(request.sitesRange());
    entity.setMessage(request.message());
    entity.setConsentGivenAt(request.consentGivenAt());
    entity.setSubmittedAt(request.submittedAt());
    return entity;
  }

  public static OnboardingRequest toDomain(OnboardingRequestJpaEntity entity) {
    return OnboardingRequest.restore(entity.getId(), entity.getType(), entity.getRequesterName(),
      new EmailAddress(entity.getEmail()), entity.getClinicName(), entity.getRequesterRole(), entity.getSitesRange(),
      entity.getMessage(), entity.getConsentGivenAt(), entity.getSubmittedAt());
  }
}
