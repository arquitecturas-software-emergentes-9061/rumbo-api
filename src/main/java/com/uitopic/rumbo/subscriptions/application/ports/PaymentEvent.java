package com.uitopic.rumbo.subscriptions.application.ports;
import java.util.UUID;
public record PaymentEvent(String id, String type, UUID orderId, String providerSubscriptionId, boolean paid) { }
