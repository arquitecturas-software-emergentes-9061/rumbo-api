package com.uitopic.rumbo.subscriptions.domain.repositories;
import java.time.Instant;
public interface ProcessedPaymentEventRepository {
  boolean claim(String eventId, String eventType, Instant processedAt);
}
