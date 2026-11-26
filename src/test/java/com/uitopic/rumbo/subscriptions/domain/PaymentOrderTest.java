package com.uitopic.rumbo.subscriptions.domain;

import com.uitopic.rumbo.subscriptions.domain.model.aggregates.PaymentOrder;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class PaymentOrderTest {
  private final OrganizationRef organization = new OrganizationRef(UUID.randomUUID(), "Clinic");
  private PaymentOrder order() { return PaymentOrder.create(UUID.randomUUID(), organization, com.uitopic.rumbo.subscriptions.support.BillingFixture.billing(), PlanCode.BASIC, new Money(new BigDecimal("39.90"), "PEN")); }
  @Test void createsPendingSnapshotAndAttachesSessionOnce() {
    var order = order();
    assertThat(order.status()).isEqualTo(PaymentOrderStatus.PENDING);
    assertThat(order.providerSessionId()).isNull();
    assertThat(order.amount().amount()).isEqualTo(new BigDecimal("39.90"));
    assertThat(order.billing()).isEqualTo(com.uitopic.rumbo.subscriptions.support.BillingFixture.billing());
    assertThat(order.paidAt()).isNull();
    order.attachCheckoutSession("session-id");
    assertThat(order.organization()).isSameAs(organization);
    assertThat(order.providerSessionId()).isEqualTo("session-id");
    assertThat(order.updatedAt()).isAfterOrEqualTo(order.createdAt());
    assertThatThrownBy(() -> order.attachCheckoutSession("another")).isInstanceOf(IllegalStateException.class);
  }
  @Test void requiresOrganization() {
    assertThatNullPointerException().isThrownBy(() -> PaymentOrder.create(UUID.randomUUID(), null, com.uitopic.rumbo.subscriptions.support.BillingFixture.billing(), PlanCode.BASIC, new Money(BigDecimal.ONE, "PEN")));
  }
  @Test void requiresBillingForNewOrders() {
    assertThatThrownBy(() -> PaymentOrder.create(UUID.randomUUID(), organization, null, PlanCode.BASIC, new Money(BigDecimal.ONE, "PEN")))
      .isInstanceOf(com.uitopic.rumbo.subscriptions.domain.exceptions.InvalidBillingDetailsException.class);
  }
  @Test void paidAtIsConfirmationTimeAndIsStableOnRepeatedEvents() {
    var order = order();
    var now = java.time.Instant.parse("2026-11-12T16:00:00Z");
    order.markPaid(now);
    order.markPaid(now.plusSeconds(10));
    order.markExpired(now.plusSeconds(20));
    assertThat(order.paidAt()).isEqualTo(now);
    assertThat(order.billing()).isEqualTo(com.uitopic.rumbo.subscriptions.support.BillingFixture.billing());
  }
  @Test void rejectsMissingIdentityAndInvalidSession() {
    assertThatNullPointerException().isThrownBy(() -> PaymentOrder.create(null, new OrganizationRef(UUID.randomUUID(), "Clinic"), com.uitopic.rumbo.subscriptions.support.BillingFixture.billing(), PlanCode.BASIC, new Money(BigDecimal.ONE, "PEN")));
    var order = order();
    assertThatIllegalArgumentException().isThrownBy(() -> order.attachCheckoutSession(" "));
    assertThatIllegalArgumentException().isThrownBy(() -> order.attachCheckoutSession(null));
    assertThatIllegalArgumentException().isThrownBy(() -> order.attachCheckoutSession("a".repeat(256)));
  }
  @ParameterizedTest @EnumSource(value = PaymentOrderStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
  void terminalStatesIgnoreWebhookTransitionsAndRejectOtherTransitions(PaymentOrderStatus target) {
    var order = order();
    switch (target) {
      case PAID -> order.markPaid();
      case FAILED -> order.markFailed();
      case EXPIRED -> order.markExpired();
      case CANCELLED -> order.markCancelled();
      default -> throw new AssertionError();
    }
    assertThat(order.organization()).isSameAs(organization);
    assertThat(order.status()).isEqualTo(target);
    var updatedAt = order.updatedAt();
    var paidAt = order.paidAt();
    order.markPaid();
    assertThatIllegalStateException().isThrownBy(order::markFailed);
    order.markExpired();
    assertThat(order.organization()).isSameAs(organization);
    assertThat(order.status()).isEqualTo(target);
    assertThat(order.updatedAt()).isEqualTo(updatedAt);
    assertThat(order.paidAt()).isEqualTo(paidAt);
    assertThat(order.billing()).isEqualTo(com.uitopic.rumbo.subscriptions.support.BillingFixture.billing());
    assertThatIllegalStateException().isThrownBy(order::markCancelled);
    assertThatIllegalStateException().isThrownBy(() -> order.attachCheckoutSession("session"));
  }
}
