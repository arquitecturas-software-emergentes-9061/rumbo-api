package com.uitopic.rumbo.iam.infrastructure.persistence.jpa.adapters;

import com.uitopic.rumbo.iam.domain.model.aggregates.OnboardingRequest;
import com.uitopic.rumbo.iam.domain.repositories.OnboardingRequestRepository;
import com.uitopic.rumbo.iam.infrastructure.persistence.jpa.mappers.OnboardingRequestJpaMapper;
import com.uitopic.rumbo.iam.infrastructure.persistence.jpa.repositories.OnboardingRequestJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter of {@link OnboardingRequestRepository}. */
@Repository
public class OnboardingRequestRepositoryImpl implements OnboardingRequestRepository {
  private final OnboardingRequestJpaRepository jpa;

  public OnboardingRequestRepositoryImpl(OnboardingRequestJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public OnboardingRequest save(OnboardingRequest request) {
    return OnboardingRequestJpaMapper.toDomain(jpa.save(OnboardingRequestJpaMapper.toEntity(request)));
  }

  @Override
  public Optional<OnboardingRequest> findById(UUID id) {
    return jpa.findById(id).map(OnboardingRequestJpaMapper::toDomain);
  }
}
