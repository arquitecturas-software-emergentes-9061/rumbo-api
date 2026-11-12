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
  private PaymentOrder order() { return PaymentOrder.create(UUID.randomUUID(), PlanCode.BASIC, new Money(new BigDecimal("39.90"), "PEN")); }
  @Test void createsPendingSnapshotAndAttachesSessionOnce() {
    var order = order();
    assertThat(order.status()).isEqualTo(PaymentOrderStatus.PENDING);
    assertThat(order.providerSessionId()).isNull();
    assertThat(order.amount().amount()).isEqualTo(new BigDecimal("39.90"));
    order.attachCheckoutSession("session-id");
    assertThat(order.providerSessionId()).isEqualTo("session-id");
    assertThat(order.updatedAt()).isAfterOrEqualTo(order.createdAt());
    assertThatThrownBy(() -> order.attachCheckoutSession("another")).isInstanceOf(IllegalStateException.class);
  }
  @Test void rejectsMissingIdentityAndInvalidSession() {
    assertThatNullPointerException().isThrownBy(() -> PaymentOrder.create(null, PlanCode.BASIC, new Money(BigDecimal.ONE, "PEN")));
    var order = order();
    assertThatIllegalArgumentException().isThrownBy(() -> order.attachCheckoutSession(" "));
    assertThatIllegalArgumentException().isThrownBy(() -> order.attachCheckoutSession(null));
    assertThatIllegalArgumentException().isThrownBy(() -> order.attachCheckoutSession("a".repeat(256)));
  }
  @ParameterizedTest @EnumSource(value = PaymentOrderStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
  void terminalStatesRejectEveryFurtherTransition(PaymentOrderStatus target) {
    var order = order();
    switch (target) {
      case PAID -> order.markPaid();
      case FAILED -> order.markFailed();
      case EXPIRED -> order.markExpired();
      case CANCELLED -> order.markCancelled();
      default -> throw new AssertionError();
    }
    assertThat(order.status()).isEqualTo(target);
    assertThatIllegalStateException().isThrownBy(order::markPaid);
    assertThatIllegalStateException().isThrownBy(order::markFailed);
    assertThatIllegalStateException().isThrownBy(order::markExpired);
    assertThatIllegalStateException().isThrownBy(order::markCancelled);
    assertThatIllegalStateException().isThrownBy(() -> order.attachCheckoutSession("session"));
  }
}
