package com.uitopic.rumbo.subscriptions.domain.repositories;

import java.util.*;
import com.uitopic.rumbo.subscriptions.domain.model.aggregates.PaymentOrder;
public interface PaymentOrderRepository {
  PaymentOrder save(PaymentOrder order);
  Optional<PaymentOrder> findByIdAndPayerUserId(UUID id, UUID payerUserId);
}
