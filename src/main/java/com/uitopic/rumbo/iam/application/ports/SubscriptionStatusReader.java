package com.uitopic.rumbo.iam.application.ports;

import java.util.UUID;

/**
 * Outbound port to Subscriptions and Payments (TS-28, WI-TS28-02): tells IAM whether the clinic of an administrator
 * may run administrative operations. IAM never reads subscription tables; the implementation is an adapter over the
 * public facade of Subscriptions.
 * <p>Contract, implemented by {@code SubscriptionsStatusReaderAdapter} over {@code SubscriptionsStatusFacade}:
 * <ul>
 *   <li>{@code adminUserId} is the account of the {@code CLINIC_ADMIN}; the adapter resolves its clinic and the
 *   current subscription of that clinic.</li>
 *   <li>{@link Standing#ACTIVE} only while the plan is paid and enabled; a lapsed or cancelled plan is
 *   {@link Standing#EXPIRED} and a clinic that never subscribed is {@link Standing#NONE}.</li>
 *   <li>It must be fast (it runs on every administrative write) and may throw when Subscriptions is unavailable;
 *   the request is then allowed and the failure logged, so a fault there never stops the clinics.</li>
 * </ul>
 * Without an implementation registered, administrative operations are not restricted by subscription.
 */
public interface SubscriptionStatusReader {
  Standing standingOf(UUID adminUserId);

  /** Situation of the clinic's plan as far as IAM needs to know. */
  enum Standing { ACTIVE, EXPIRED, NONE }
}
