package com.uitopic.rumbo.subscriptions.domain;
import com.uitopic.rumbo.subscriptions.domain.model.aggregates.Subscription;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SubscriptionTest {
  @Test void activationUsesOneCalendarMonthInUtc() {
    var now = Instant.parse("2026-01-31T10:00:00Z");
    var owner = UUID.randomUUID();
    var s = Subscription.activate(owner, PlanCode.BASIC, "sub_test", now);
    assertThat(s.ownerUserId()).isEqualTo(owner);
    assertThat(s.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    assertThat(s.planCode()).isEqualTo(PlanCode.BASIC);
    assertThat(s.currentPeriodStart()).isEqualTo(now);
    assertThat(s.currentPeriodEnd()).isEqualTo(Instant.parse("2026-02-28T10:00:00Z"));
    assertThat(s.createdAt()).isEqualTo(now);
    assertThat(s.updatedAt()).isEqualTo(now);
    assertThat(s.providerSubscriptionId()).isEqualTo("sub_test");
  }
  @Test void changePlanRestartsPeriodAndPreservesIdentityAndCreationTime() {
    var start = Instant.parse("2026-01-31T10:00:00Z");
    var s = Subscription.activate(UUID.randomUUID(), PlanCode.BASIC, "sub_old", start);
    var id = s.id();
    var now = Instant.parse("2026-02-10T10:00:00Z");
    s.changePlan(PlanCode.PROFESSIONAL, null, now);
    assertThat(s.id()).isEqualTo(id);
    assertThat(s.createdAt()).isEqualTo(start);
    assertThat(s.planCode()).isEqualTo(PlanCode.PROFESSIONAL);
    assertThat(s.currentPeriodStart()).isEqualTo(now);
    assertThat(s.currentPeriodEnd()).isEqualTo(Instant.parse("2026-03-10T10:00:00Z"));
    assertThat(s.updatedAt()).isEqualTo(now);
    assertThat(s.providerSubscriptionId()).isNull();
  }
}
