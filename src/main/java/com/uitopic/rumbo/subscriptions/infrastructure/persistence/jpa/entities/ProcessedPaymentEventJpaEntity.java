package com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.entities;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "subscriptions_processed_payment_events")
public class ProcessedPaymentEventJpaEntity {
  @Id @Column(name = "event_id", length = 255) private String eventId;
  @Column(name = "event_type", nullable = false, length = 100) private String eventType;
  @Column(name = "processed_at", nullable = false) private Instant processedAt;
}
