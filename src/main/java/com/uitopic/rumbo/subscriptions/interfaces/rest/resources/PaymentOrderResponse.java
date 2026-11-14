package com.uitopic.rumbo.subscriptions.interfaces.rest.resources;

import java.util.UUID;
import java.math.BigDecimal;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.*;
public record PaymentOrderResponse(UUID orderId, OrganizationResponse organization, PlanCode planCode, PaymentOrderStatus status, BigDecimal amount, String currency, java.time.Instant createdAt, BillingDetailsResource billing, java.time.Instant paidAt) {}
