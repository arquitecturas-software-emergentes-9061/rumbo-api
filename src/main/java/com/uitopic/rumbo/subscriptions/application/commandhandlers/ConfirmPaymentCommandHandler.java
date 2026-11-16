package com.uitopic.rumbo.subscriptions.application.commandhandlers;

import com.uitopic.rumbo.subscriptions.application.ports.*;
import com.uitopic.rumbo.subscriptions.domain.repositories.*;
import com.uitopic.rumbo.subscriptions.domain.model.aggregates.Subscription;
import com.uitopic.rumbo.subscriptions.domain.model.events.SubscriptionActivatedEvent;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PaymentOrderStatus;
import java.time.Clock;
import org.slf4j.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfirmPaymentCommandHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ConfirmPaymentCommandHandler.class);
  private final PaymentOrderRepository orders;
  private final SubscriptionRepository subscriptions;
  private final ProcessedPaymentEventRepository events;
  private final ApplicationEventPublisher publisher;
  private final Clock clock;
  public ConfirmPaymentCommandHandler(PaymentOrderRepository orders, SubscriptionRepository subscriptions,
      ProcessedPaymentEventRepository events, ApplicationEventPublisher publisher, Clock clock) {
    this.orders = orders; this.subscriptions = subscriptions; this.events = events;
    this.publisher = publisher; this.clock = clock;
  }
  @Transactional
  public void handle(PaymentEvent event) {
    var now = clock.instant();
    // INSERT ON CONFLICT serializes duplicate deliveries and rolls back with activation.
    if (!events.claim(event.id(), event.type(), now)) return;
    boolean completed = "checkout.session.completed".equals(event.type()) && event.paid();
    boolean expired = "checkout.session.expired".equals(event.type());
    if (!completed && !expired) { LOG.info("Payment event received and ignored"); return; }
    var order = event.orderId() == null ? null : orders.findByIdForUpdate(event.orderId()).orElse(null);
    if (order == null) { LOG.warn("Payment webhook references an unknown order"); return; }
    if (order.status() != PaymentOrderStatus.PENDING) return;
    if (expired) { order.markExpired(now); orders.save(order); return; }
    // Serialize different orders for the same owner, including their first subscription.
    subscriptions.lockOwner(order.payerUserId());
    var subscription = subscriptions.findByOwnerUserId(order.payerUserId()).orElse(null);
    if (subscription == null) subscription = Subscription.activate(order.payerUserId(), order.planCode(), event.providerSubscriptionId(), now);
    else subscription.changePlan(order.planCode(), event.providerSubscriptionId(), now);
    order.markPaid(now);
    orders.save(order);
    subscriptions.save(subscription);
    publisher.publishEvent(new SubscriptionActivatedEvent(subscription.ownerUserId(), subscription.planCode(),
        subscription.currentPeriodStart(), subscription.currentPeriodEnd()));
  }
}
