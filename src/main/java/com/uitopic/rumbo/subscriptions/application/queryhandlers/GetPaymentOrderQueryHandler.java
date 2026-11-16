package com.uitopic.rumbo.subscriptions.application.queryhandlers;

import com.uitopic.rumbo.subscriptions.domain.model.aggregates.PaymentOrder;
import com.uitopic.rumbo.subscriptions.domain.model.queries.GetPaymentOrderQuery;
import com.uitopic.rumbo.subscriptions.domain.repositories.PaymentOrderRepository;
import com.uitopic.rumbo.subscriptions.domain.exceptions.PaymentOrderNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class GetPaymentOrderQueryHandler {
  private final PaymentOrderRepository orders;
  public GetPaymentOrderQueryHandler(PaymentOrderRepository orders) { this.orders = orders; }
  @Transactional(readOnly = true)
  public PaymentOrder handle(GetPaymentOrderQuery query) {
    return orders.findByIdAndPayerUserId(query.orderId(), query.requesterUserId()).orElseThrow(PaymentOrderNotFoundException::new);
  }
}
