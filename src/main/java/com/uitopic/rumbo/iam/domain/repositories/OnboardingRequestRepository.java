package com.uitopic.rumbo.iam.domain.repositories;

import com.uitopic.rumbo.iam.domain.model.aggregates.OnboardingRequest;

import java.util.Optional;
import java.util.UUID;

/** Storage of the requests left on the landing page. */
public interface OnboardingRequestRepository {
  OnboardingRequest save(OnboardingRequest request);

  Optional<OnboardingRequest> findById(UUID id);
}
