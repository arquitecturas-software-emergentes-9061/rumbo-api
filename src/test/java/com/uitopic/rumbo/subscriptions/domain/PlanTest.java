package com.uitopic.rumbo.subscriptions.domain;

import com.uitopic.rumbo.subscriptions.domain.model.aggregates.Plan;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PlanTest {
  private final Money price = new Money(new BigDecimal("39.90"), "PEN");

  @Test
  void acceptsAValidMonthlyPlan() {
    Plan plan = plan(UUID.randomUUID(), PlanCode.BASIC, "Básico", price, BillingInterval.MONTH,
      PurchaseMode.CHECKOUT, 1);
    assertThat(plan.price().amount()).isEqualTo(new BigDecimal("39.90"));
    assertThat(plan.active()).isTrue();
  }

  @Test
  void rejectsMissingRequiredValues() {
    UUID id = UUID.randomUUID();
    assertThatIllegalArgumentException().isThrownBy(() -> plan(null, PlanCode.BASIC, "Basic", price, BillingInterval.MONTH, PurchaseMode.CHECKOUT, 1));
    assertThatIllegalArgumentException().isThrownBy(() -> plan(id, null, "Basic", price, BillingInterval.MONTH, PurchaseMode.CHECKOUT, 1));
    assertThatIllegalArgumentException().isThrownBy(() -> plan(id, PlanCode.BASIC, "Basic", null, BillingInterval.MONTH, PurchaseMode.CHECKOUT, 1));
    assertThatIllegalArgumentException().isThrownBy(() -> plan(id, PlanCode.BASIC, "Basic", price, null, PurchaseMode.CHECKOUT, 1));
    assertThatIllegalArgumentException().isThrownBy(() -> plan(id, PlanCode.BASIC, "Basic", price, BillingInterval.MONTH, null, 1));
  }

  @Test
  void rejectsInvalidNamesAndDisplayOrder() {
    for (String name : new String[]{null, "", "  ", "a".repeat(61)}) {
      assertThatIllegalArgumentException().isThrownBy(() -> plan(UUID.randomUUID(), PlanCode.BASIC, name, price, BillingInterval.MONTH, PurchaseMode.CHECKOUT, 1));
    }
    assertThatIllegalArgumentException().isThrownBy(() -> plan(UUID.randomUUID(), PlanCode.BASIC, "Basic", price, BillingInterval.MONTH, PurchaseMode.CHECKOUT, -1));
  }

  @Test
  void invalidPricesCannotEnterAPlan() {
    for (String amount : new String[]{"0", "-1", "39.901"}) {
      assertThatIllegalArgumentException().isThrownBy(() -> plan(UUID.randomUUID(), PlanCode.BASIC, "Basic",
        new Money(new BigDecimal(amount), "PEN"), BillingInterval.MONTH, PurchaseMode.CHECKOUT, 1));
    }
    assertThatIllegalArgumentException().isThrownBy(() -> plan(UUID.randomUUID(), PlanCode.BASIC, "Basic",
      new Money(BigDecimal.ONE, "ZZZ"), BillingInterval.MONTH, PurchaseMode.CHECKOUT, 1));
  }

  private Plan plan(UUID id, PlanCode code, String name, Money money, BillingInterval interval,
                    PurchaseMode mode, int order) {
    return new Plan(id, code, name, money, interval, mode, order, true);
  }
}
