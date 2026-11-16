package com.uitopic.rumbo.subscriptions.interfaces.acl;

import java.util.UUID;

/**
 * Stable read-only Subscriptions contract that tells other bounded contexts whether an account's plan is in force
 * (TS-28, WI-TS28-02). IAM uses it to authorize administrative writes without reading subscription tables.
 */
public interface SubscriptionsStatusFacade {
  /** Plan situation of the clinic owned by {@code ownerUserId}. */
  Standing standingOf(UUID ownerUserId);

  /** {@code ACTIVE} while paid and in force, {@code EXPIRED} when lapsed or cancelled, {@code NONE} if never bought. */
  enum Standing { ACTIVE, EXPIRED, NONE }
}
