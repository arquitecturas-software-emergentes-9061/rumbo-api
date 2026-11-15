package com.uitopic.rumbo.iam.infrastructure.persistence.jpa.repositories;

import com.uitopic.rumbo.iam.infrastructure.persistence.jpa.entities.OnboardingRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OnboardingRequestJpaRepository extends JpaRepository<OnboardingRequestJpaEntity, UUID> {
}
