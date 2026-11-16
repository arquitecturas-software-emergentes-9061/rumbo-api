package com.uitopic.rumbo.iam.infrastructure.acl;

import com.uitopic.rumbo.iam.application.ports.SubscriptionStatusReader;
import com.uitopic.rumbo.subscriptions.interfaces.acl.SubscriptionsStatusFacade;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Anticorruption layer from Subscriptions and Payments to IAM (TS-28, WI-TS28-02). The administrator owns the clinic's
 * sites, so its account id is the owner the subscription is registered under.
 */
@Component
public class SubscriptionsStatusReaderAdapter implements SubscriptionStatusReader {
  private final SubscriptionsStatusFacade subscriptions;

  public SubscriptionsStatusReaderAdapter(SubscriptionsStatusFacade subscriptions) {
    this.subscriptions = subscriptions;
  }

  @Override
  public Standing standingOf(UUID adminUserId) {
    return switch (subscriptions.standingOf(adminUserId)) {
      case ACTIVE -> Standing.ACTIVE;
      case EXPIRED -> Standing.EXPIRED;
      case NONE -> Standing.NONE;
    };
  }
}
