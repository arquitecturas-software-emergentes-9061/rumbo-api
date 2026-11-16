package com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.repositories;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.entities.SubscriptionJpaEntity;
public interface SpringDataSubscriptionRepository extends JpaRepository<SubscriptionJpaEntity, UUID> {
  Optional<SubscriptionJpaEntity> findByOwnerUserId(UUID ownerUserId);
}
