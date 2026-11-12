package com.uitopic.rumbo.subscriptions.application;
import com.uitopic.rumbo.subscriptions.application.commandhandlers.ConfirmPaymentCommandHandler;
import com.uitopic.rumbo.subscriptions.application.ports.PaymentEvent;
import com.uitopic.rumbo.subscriptions.domain.repositories.*;
import com.uitopic.rumbo.subscriptions.domain.model.aggregates.*;
import com.uitopic.rumbo.subscriptions.domain.model.events.SubscriptionActivatedEvent;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ConfirmPaymentCommandHandlerTest {
  final PaymentOrderRepository orders = mock(PaymentOrderRepository.class);
  final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
  final ProcessedPaymentEventRepository events = mock(ProcessedPaymentEventRepository.class);
  final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  final Instant now = Instant.parse("2026-01-31T12:00:00Z");
  final ConfirmPaymentCommandHandler handler = new ConfirmPaymentCommandHandler(orders, subscriptions, events, publisher, Clock.fixed(now, ZoneOffset.UTC));
  final PaymentOrder order = PaymentOrder.create(UUID.randomUUID(), new com.uitopic.rumbo.subscriptions.domain.model.valueobjects.OrganizationRef(UUID.randomUUID(), "Clinic"), PlanCode.BASIC, new Money(BigDecimal.ONE, "PEN"));
  PaymentEvent event(String type, boolean paid) { return new PaymentEvent("evt_test", type, order.id(), "sub_test", paid); }
  @BeforeEach void setup() {
    when(events.claim(anyString(), anyString(), eq(now))).thenReturn(true);
    when(orders.findByIdForUpdate(order.id())).thenReturn(Optional.of(order));
    when(subscriptions.findByOwnerUserId(order.payerUserId())).thenReturn(Optional.empty());
  }
  @Test void paidOrderActivatesSubscriptionAndPublishesEventWithFixedPeriod() {
    handler.handle(event("checkout.session.completed", true));
    assertThat(order.status()).isEqualTo(PaymentOrderStatus.PAID);
    assertThat(order.updatedAt()).isEqualTo(now);
    verify(subscriptions).save(argThat(s -> s.ownerUserId().equals(order.payerUserId()) && s.status() == SubscriptionStatus.ACTIVE
        && s.currentPeriodStart().equals(now) && s.currentPeriodEnd().equals(Instant.parse("2026-02-28T12:00:00Z"))));
    verify(publisher).publishEvent(new SubscriptionActivatedEvent(order.payerUserId(), PlanCode.BASIC, now, Instant.parse("2026-02-28T12:00:00Z")));
  }
  @Test void repeatedEventDoesNothing() {
    when(events.claim(anyString(), anyString(), eq(now))).thenReturn(false);
    handler.handle(event("checkout.session.completed", true));
    verifyNoInteractions(orders, subscriptions, publisher);
  }
  @Test void anotherEventForPaidOrderDoesNotReactivate() {
    order.markPaid();
    handler.handle(event("checkout.session.completed", true));
    verifyNoInteractions(subscriptions, publisher);
    verify(orders, never()).save(any());
  }
  @Test void unknownOrderIsAcknowledged() {
    when(orders.findByIdForUpdate(order.id())).thenReturn(Optional.empty());
    handler.handle(event("checkout.session.completed", true));
    verifyNoInteractions(subscriptions, publisher);
  }
  @Test void expirationOnlyExpiresPendingOrder() {
    handler.handle(event("checkout.session.expired", false));
    assertThat(order.status()).isEqualTo(PaymentOrderStatus.EXPIRED);
    assertThat(order.updatedAt()).isEqualTo(now);
    verify(orders).save(order);
    verifyNoInteractions(subscriptions, publisher);
  }
  @Test void unpaidAndUnrelatedEventsAreRecordedAndIgnored() {
    handler.handle(event("checkout.session.completed", false));
    handler.handle(event("invoice.paid", true));
    verifyNoInteractions(orders, subscriptions, publisher);
  }
  @Test void existingSubscriptionChangesPlanAndRestartsPeriod() {
    var s = Subscription.activate(order.payerUserId(), PlanCode.PROFESSIONAL, "sub_old", now.minusSeconds(900));
    var id = s.id();
    when(subscriptions.findByOwnerUserId(order.payerUserId())).thenReturn(Optional.of(s));
    handler.handle(event("checkout.session.completed", true));
    verify(subscriptions).save(s);
    assertThat(s.id()).isEqualTo(id);
    assertThat(s.planCode()).isEqualTo(PlanCode.BASIC);
    assertThat(s.currentPeriodStart()).isEqualTo(now);
    assertThat(s.providerSubscriptionId()).isEqualTo("sub_test");
  }
}
