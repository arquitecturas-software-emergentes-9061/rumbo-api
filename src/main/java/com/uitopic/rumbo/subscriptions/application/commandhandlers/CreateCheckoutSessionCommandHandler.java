package com.uitopic.rumbo.subscriptions.application.commandhandlers;

import com.uitopic.rumbo.subscriptions.domain.model.aggregates.PaymentOrder;
import com.uitopic.rumbo.subscriptions.domain.model.commands.CreateCheckoutSessionCommand;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PurchaseMode;
import com.uitopic.rumbo.subscriptions.domain.repositories.*;
import com.uitopic.rumbo.subscriptions.domain.exceptions.*;
import com.uitopic.rumbo.subscriptions.application.ports.PaymentGateway;
import com.uitopic.rumbo.subscriptions.application.exceptions.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CreateCheckoutSessionCommandHandler {
  private final PlanRepository plans;
  private final PaymentOrderRepository orders;
  private final PaymentGateway gateway;
  private final TransactionTemplate transaction;
  public CreateCheckoutSessionCommandHandler(PlanRepository plans, PaymentOrderRepository orders,
      PaymentGateway gateway, PlatformTransactionManager manager) {
    this.plans = plans; this.orders = orders; this.gateway = gateway;
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }
  // Suspend even a caller's transaction while invoking the external provider.
  @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  public Result handle(CreateCheckoutSessionCommand command) {
    PaymentOrder order = transaction.execute(status -> {
      var plan = plans.findByCode(command.planCode()).filter(p -> p.active()).orElseThrow(PlanNotFoundException::new);
      if (plan.purchaseMode() == PurchaseMode.CONTACT_SALES) throw new PlanRequiresSalesContactException();
      return orders.save(PaymentOrder.create(command.payerUserId(), plan.code(), plan.price()));
    });
    PaymentGateway.CheckoutSession session;
    try {
      session = gateway.createCheckoutSession(new PaymentGateway.CheckoutRequest(
        order.id(), order.payerUserId(), order.planCode(), order.amount()));
    } catch (PaymentsUnavailableException | PaymentProviderException error) {
      transaction.executeWithoutResult(status -> { order.markFailed(); orders.save(order); });
      throw error;
    }
    transaction.executeWithoutResult(status -> { order.attachCheckoutSession(session.sessionId()); orders.save(order); });
    return new Result(order, session.url());
  }
  public record Result(PaymentOrder order, String checkoutUrl) {}
}
