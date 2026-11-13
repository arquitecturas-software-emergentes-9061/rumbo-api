package com.uitopic.rumbo.subscriptions.domain.repositories;
import com.uitopic.rumbo.subscriptions.domain.model.aggregates.Subscription;
import java.util.*;
public interface SubscriptionRepository {
  void lockOwner(UUID ownerUserId);
  Optional<Subscription> findByOwnerUserId(UUID ownerUserId);
  Subscription save(Subscription subscription);
}
