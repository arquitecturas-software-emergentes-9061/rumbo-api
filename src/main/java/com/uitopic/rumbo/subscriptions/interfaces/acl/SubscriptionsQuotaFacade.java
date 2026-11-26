package com.uitopic.rumbo.subscriptions.interfaces.acl;

import java.util.UUID;

public interface SubscriptionsQuotaFacade {
  QuotaDecision checkCanRegister(UUID ownerUserId, QuotaResource resource, long currentUsage);
}
