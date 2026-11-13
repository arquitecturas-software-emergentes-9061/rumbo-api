package com.uitopic.rumbo.subscriptions.application.acl;

import com.uitopic.rumbo.subscriptions.application.ports.PlanQuotaCatalog;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.ResourceKind;
import com.uitopic.rumbo.subscriptions.domain.repositories.SubscriptionRepository;
import com.uitopic.rumbo.subscriptions.interfaces.acl.*;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionsQuotaFacadeImpl implements SubscriptionsQuotaFacade {
  private final SubscriptionRepository subscriptions;
  private final PlanQuotaCatalog quotas;
  private final Clock clock;

  public SubscriptionsQuotaFacadeImpl(SubscriptionRepository subscriptions, PlanQuotaCatalog quotas, Clock clock) {
    this.subscriptions = subscriptions;
    this.quotas = quotas;
    this.clock = clock;
  }

  @Override
  public QuotaDecision checkCanRegister(UUID ownerUserId, QuotaResource resource, long currentUsage) {
    var subscription = subscriptions.findByOwnerUserId(ownerUserId);
    if (subscription.isEmpty() || !subscription.get().isActiveAt(clock.instant())) {
      return new QuotaDecision(QuotaDecision.Status.SUBSCRIPTION_REQUIRED, null);
    }
    var kind = switch (resource) {
      case SITE -> ResourceKind.SITE;
      case FLOOR -> ResourceKind.FLOOR;
      case BEACON -> ResourceKind.BEACON;
    };
    var quota = quotas.quotaFor(subscription.get().planCode());
    return quota.allows(kind, currentUsage)
        ? new QuotaDecision(QuotaDecision.Status.ALLOWED, null)
        : new QuotaDecision(QuotaDecision.Status.QUOTA_EXCEEDED, quota.limit(kind));
  }
}
