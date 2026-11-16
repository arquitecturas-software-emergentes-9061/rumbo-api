package com.uitopic.rumbo.subscriptions.domain.model.queries;

import java.util.UUID;
import java.util.Objects;
public record GetPaymentOrderQuery(UUID orderId, UUID requesterUserId) {
  public GetPaymentOrderQuery { Objects.requireNonNull(orderId); Objects.requireNonNull(requesterUserId); }
}
