package com.uitopic.rumbo.iam.infrastructure.security;

import com.uitopic.rumbo.iam.application.ports.SubscriptionStatusReader;
import com.uitopic.rumbo.iam.application.ports.SubscriptionStatusReader.Standing;
import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Lets administrative writes on sites through only while the clinic's plan is active (TS-28, WI-TS28-02). It runs in
 * the security chain for the routes listed in {@link SecurityConfiguration#SUBSCRIPTION_BOUND_WRITES}, so no
 * controller has to remember it, and before the site ownership checks of each controller.
 * <ul>
 *   <li>Anonymous request: denied, so the chain answers 401 as for any protected route.</li>
 *   <li>Not an administrator, or a principal that does not come from a Rumbo token: allowed here, as
 *   {@code authenticated()} would; the role and ownership rules of the controller still decide.</li>
 *   <li>Administrator without an active plan: {@link SubscriptionInactiveException}, answered as 403
 *   {@code SUBSCRIPTION_INACTIVE}.</li>
 *   <li>Subscriptions not deployed yet, or failing: allowed, and a failure is logged.</li>
 * </ul>
 * Reads stay open with an expired plan, so the clinic still sees its data and patients keep navigating.
 */
@Component
public class SubscriptionAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
  private static final Logger log = LoggerFactory.getLogger(SubscriptionAuthorizationManager.class);
  private static final AuthorizationDecision GRANTED = new AuthorizationDecision(true);
  private final ObjectProvider<SubscriptionStatusReader> readers;

  public SubscriptionAuthorizationManager(ObjectProvider<SubscriptionStatusReader> readers) {
    this.readers = readers;
  }

  @Override
  public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
    Authentication caller = authentication.get();
    if (caller == null || caller instanceof AnonymousAuthenticationToken || !caller.isAuthenticated()) {
      return new AuthorizationDecision(false);
    }
    if (!(caller.getPrincipal() instanceof AuthenticatedPrincipal principal) || !principal.isClinicAdmin()) {
      return GRANTED;
    }
    SubscriptionStatusReader reader = readers.getIfAvailable();
    if (reader == null) return GRANTED;
    Standing standing;
    try {
      standing = reader.standingOf(principal.userId());
    } catch (RuntimeException unavailable) {
      log.warn("Subscription of account {} could not be checked; the request is allowed", principal.userId(),
        unavailable);
      return GRANTED;
    }
    if (standing != Standing.ACTIVE) throw new SubscriptionInactiveException(standing);
    return GRANTED;
  }
}
