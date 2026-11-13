package com.uitopic.rumbo.subscriptions.infrastructure;

import com.uitopic.rumbo.subscriptions.domain.model.aggregates.PaymentOrder;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
import com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.mappers.PaymentOrderPersistenceMapper;
import com.uitopic.rumbo.subscriptions.support.BillingFixture;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaymentOrderPersistenceMapperTest {
  private PaymentOrder order() {
    return PaymentOrder.create(UUID.randomUUID(), new OrganizationRef(UUID.randomUUID(), "Clinic"),
      BillingFixture.billing(), PlanCode.BASIC, new Money(BigDecimal.ONE, "PEN"));
  }
  @Test void mapsEveryBillingColumnAndConfirmationTimeInBothDirections() {
    var order = order();
    var now = Instant.parse("2026-11-12T16:00:00Z");
    order.markPaid(now);
    var entity = PaymentOrderPersistenceMapper.toEntity(order);
    assertThat(entity.getBillingLegalName()).isEqualTo(order.billing().legalName());
    assertThat(entity.getBillingRuc()).isEqualTo(order.billing().ruc());
    assertThat(entity.getBillingFiscalAddress()).isEqualTo(order.billing().fiscalAddress());
    assertThat(entity.getBillingEmail()).isEqualTo(order.billing().billingEmail());
    assertThat(entity.getBillingPurchaseOrder()).isEqualTo(order.billing().purchaseOrder());
    assertThat(entity.getPaidAt()).isEqualTo(now);
    var restored = PaymentOrderPersistenceMapper.toDomain(entity);
    assertThat(restored.billing()).isEqualTo(order.billing());
    assertThat(restored.paidAt()).isEqualTo(now);
    assertThat(restored.status()).isEqualTo(PaymentOrderStatus.PAID);
  }
  @Test void historicalNullBillingCanBeReadAndSavedWithoutInventingData() {
    var entity = PaymentOrderPersistenceMapper.toEntity(order());
    entity.setBillingLegalName(null); entity.setBillingRuc(null);
    entity.setBillingFiscalAddress(null); entity.setBillingEmail(null); entity.setBillingPurchaseOrder(null);
    var historical = PaymentOrderPersistenceMapper.toDomain(entity);
    assertThat(historical.billing()).isNull();
    assertThat(historical.paidAt()).isNull();
    historical.markPaid();
    var saved = PaymentOrderPersistenceMapper.toEntity(historical);
    assertThat(saved.getBillingLegalName()).isNull();
    assertThat(saved.getBillingRuc()).isNull();
    assertThat(saved.getBillingFiscalAddress()).isNull();
    assertThat(saved.getBillingEmail()).isNull();
    assertThat(saved.getBillingPurchaseOrder()).isNull();
    assertThat(saved.getPaidAt()).isNotNull();
  }
}
