package com.uitopic.rumbo.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.uitopic.rumbo.iam.application.ports.SubscriptionStatusReader;
import com.uitopic.rumbo.iam.application.ports.SubscriptionStatusReader.Standing;
import com.uitopic.rumbo.iam.domain.model.valueobjects.UserRole;
import com.uitopic.rumbo.iam.infrastructure.security.SubscriptionAuthorizationManager;
import com.uitopic.rumbo.iam.infrastructure.security.SubscriptionInactiveException;
import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/** TS-28 (WI-TS28-02 and part of WI-TS28-05): administrative writes need an active plan. */
class SubscriptionAuthorizationManagerTest {
  private final UUID adminId = UUID.randomUUID();
  private final SubscriptionStatusReader reader = mock(SubscriptionStatusReader.class);
  private final RequestAuthorizationContext context =
    new RequestAuthorizationContext(new MockHttpServletRequest("POST", "/api/v1/sites"));

  @Test
  void anActivePlanLetsTheAdministratorWrite() {
    when(reader.standingOf(adminId)).thenReturn(Standing.ACTIVE);

    assertThat(manager(reader).check(() -> as(UserRole.CLINIC_ADMIN), context).isGranted()).isTrue();
  }

  @Test
  void anExpiredPlanIsDeniedWithItsOwnReason() {
    when(reader.standingOf(adminId)).thenReturn(Standing.EXPIRED);

    assertThatThrownBy(() -> manager(reader).check(() -> as(UserRole.CLINIC_ADMIN), context))
      .isInstanceOf(SubscriptionInactiveException.class)
      .satisfies(error -> assertThat(((SubscriptionInactiveException) error).standing()).isEqualTo(Standing.EXPIRED));
  }

  @Test
  void aClinicThatNeverSubscribedIsDenied() {
    when(reader.standingOf(adminId)).thenReturn(Standing.NONE);

    assertThatThrownBy(() -> manager(reader).check(() -> as(UserRole.CLINIC_ADMIN), context))
      .isInstanceOf(SubscriptionInactiveException.class)
      .hasMessageContaining("no subscription");
  }

  @Test
  void anonymousRequestsAreDeniedSoTheChainAsksForAToken() {
    var anonymous = new AnonymousAuthenticationToken("key", "anonymous",
      List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

    assertThat(manager(reader).check(() -> anonymous, context).isGranted()).isFalse();
    verifyNoInteractions(reader);
  }

  @Test
  void patientsAreLeftToTheRoleRulesOfTheController() {
    assertThat(manager(reader).check(() -> as(UserRole.PATIENT), context).isGranted()).isTrue();
    verifyNoInteractions(reader);
  }

  @Test
  void principalsThatDoNotComeFromARumboTokenAreLeftToTheControllerRules() {
    var springUser = UsernamePasswordAuthenticationToken.authenticated("admin", null,
      List.of(new SimpleGrantedAuthority("ROLE_CLINIC_ADMIN")));

    assertThat(manager(reader).check(() -> springUser, context).isGranted()).isTrue();
    verifyNoInteractions(reader);
  }

  @Test
  void withoutSubscriptionsDeployedNothingIsRestricted() {
    assertThat(manager(null).check(() -> as(UserRole.CLINIC_ADMIN), context).isGranted()).isTrue();
  }

  @Test
  void aFailureInSubscriptionsNeverStopsTheClinic() {
    when(reader.standingOf(adminId)).thenThrow(new IllegalStateException("subscriptions down"));

    assertThat(manager(reader).check(() -> as(UserRole.CLINIC_ADMIN), context).isGranted()).isTrue();
  }

  private SubscriptionAuthorizationManager manager(SubscriptionStatusReader available) {
    StaticListableBeanFactory factory = new StaticListableBeanFactory();
    if (available != null) factory.addBean("reader", available);
    return new SubscriptionAuthorizationManager(factory.getBeanProvider(SubscriptionStatusReader.class));
  }

  private Authentication as(UserRole role) {
    var principal = new AuthenticatedPrincipal(adminId, role, UUID.randomUUID(), null);
    return UsernamePasswordAuthenticationToken.authenticated(principal, null,
      List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
  }
}
