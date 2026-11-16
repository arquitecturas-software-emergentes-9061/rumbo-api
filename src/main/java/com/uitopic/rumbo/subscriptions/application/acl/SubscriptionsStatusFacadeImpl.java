package com.uitopic.rumbo.subscriptions.application.acl;

import com.uitopic.rumbo.subscriptions.domain.repositories.SubscriptionRepository;
import com.uitopic.rumbo.subscriptions.interfaces.acl.SubscriptionsStatusFacade;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the owner's subscription with the same rule as the quota facade: active only while {@code isActiveAt(now)}. */
@Service
public class SubscriptionsStatusFacadeImpl implements SubscriptionsStatusFacade {
  private final SubscriptionRepository subscriptions;
  private final Clock clock;

  public SubscriptionsStatusFacadeImpl(SubscriptionRepository subscriptions, Clock clock) {
    this.subscriptions = subscriptions;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public Standing standingOf(UUID ownerUserId) {
    return subscriptions.findByOwnerUserId(ownerUserId)
        .map(subscription -> subscription.isActiveAt(clock.instant()) ? Standing.ACTIVE : Standing.EXPIRED)
        .orElse(Standing.NONE);
  }
}
