package com.uitopic.rumbo.subscriptions.interfaces.rest.resources;

import java.util.UUID;
import java.math.BigDecimal;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
public record CheckoutSessionResponse(UUID orderId, PlanCode planCode, PaymentOrderStatus status, BigDecimal amount, String currency, String checkoutUrl) {}
