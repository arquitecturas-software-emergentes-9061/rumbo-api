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

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Lets administrative writes on sites through only while the clinic's plan is active (TS-28, WI-TS28-02). It runs in
 * the security chain for the routes listed in {@link SecurityConfiguration#SUBSCRIPTION_BOUND_WRITES} (except the creations in
 * {@link SecurityConfiguration#QUOTA_DECIDED_CREATIONS}, which the quota guard of Planning decides), so no
 * controller has to remember it, and before the site ownership checks of each controller.
 * <ul>
 *   <li>Anonymous request: denied, so the chain answers 401 as for any protected route.</li>
 *   <li>Not an administrator, or a principal that does not come from a Rumbo token: allowed here, as
 *   {@code authenticated()} would; the role and ownership rules of the controller still decide.</li>
 *   <li>Administrator without an active plan: {@link SubscriptionRequiredException}, answered as 403
 *   {@code SUBSCRIPTION_REQUIRED}.</li>
 *   <li>Subscriptions not deployed yet, or failing: allowed, and a failure is logged.</li>
 * </ul>
 * Reads stay open with an expired plan, so the clinic still sees its data and patients keep navigating.
 */
@Component
public class SubscriptionAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
  private static final Logger log = LoggerFactory.getLogger(SubscriptionAuthorizationManager.class);
  private static final AuthorizationDecision GRANTED = new AuthorizationDecision(true);
  private static final String SITES = "/api/v1/sites/";
  private final ObjectProvider<SubscriptionStatusReader> readers;
  private final ObjectProvider<SiteOwnerGuard> owners;

  public SubscriptionAuthorizationManager(ObjectProvider<SubscriptionStatusReader> readers,
                                          ObjectProvider<SiteOwnerGuard> owners) {
    this.readers = readers;
    this.owners = owners;
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
    if (isAnotherAdministratorsSite(caller, context.getRequest())) return GRANTED;
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
    if (standing != Standing.ACTIVE) throw new SubscriptionRequiredException(standing);
    return GRANTED;
  }

  /**
   * A site of another clinic is answered 404 by the ownership rules of the controller; checking the caller's plan first
   * would turn it into a 403 and reveal that the site exists.
   */
  private boolean isAnotherAdministratorsSite(Authentication caller, HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (!path.startsWith(SITES)) return false;
    String segment = path.substring(SITES.length()).split("/", 2)[0];
    SiteOwnerGuard guard = owners.getIfAvailable();
    if (guard == null) return false;
    try {
      return !guard.isOwner(caller, UUID.fromString(segment));
    } catch (IllegalArgumentException notASiteId) {
      return false;
    }
  }
}