package com.uitopic.rumbo.communications.infrastructure.persistence.jpa.entities;

import com.uitopic.rumbo.shared.infrastructure.persistence.jpa.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Row of {@code communications_notifications}. Only the mapper converts it to and from the {@code Notification}
 * aggregate. Columns must stay aligned with {@code V202611151100__communications_create_notifications.sql} and
 * {@code V202611271100__communications_operational_notifications.sql}.
 */
@Entity
@Table(name = "communications_notifications")
public class NotificationJpaEntity extends AuditableJpaEntity {
  @Id
  @Column(nullable = false)
  private UUID id;

  @Column(name = "recipient_user_id", nullable = false)
  private UUID recipientUserId;

  @Column(nullable = false, length = 40)
  private String type;

  @Column(nullable = false, length = 20)
  private String channel;

  @Column(length = 255)
  private String detail;

  @Column(name = "site_id")
  private UUID siteId;

  @Column(name = "source_event_id")
  private UUID sourceEventId;

  @Column(nullable = false, length = 10)
  private String priority;

  @Column(name = "delivery_status", nullable = false, length = 20)
  private String deliveryStatus;

  @Column(name = "delivery_attempts", nullable = false)
  private int deliveryAttempts;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "read_at")
  private Instant readAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getRecipientUserId() { return recipientUserId; }
  public void setRecipientUserId(UUID recipientUserId) { this.recipientUserId = recipientUserId; }
  public String getType() { return type; }
  public void setType(String type) { this.type = type; }
  public String getChannel() { return channel; }
  public void setChannel(String channel) { this.channel = channel; }
  public String getDetail() { return detail; }
  public void setDetail(String detail) { this.detail = detail; }
  public UUID getSiteId() { return siteId; }

  public void setSiteId(UUID siteId) { this.siteId = siteId; }

  public UUID getSourceEventId() { return sourceEventId; }

  public void setSourceEventId(UUID sourceEventId) { this.sourceEventId = sourceEventId; }

  public String getPriority() { return priority; }

  public void setPriority(String priority) { this.priority = priority; }

  public Instant getNextAttemptAt() { return nextAttemptAt; }

  public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }

  public String getDeliveryStatus() { return deliveryStatus; }
  public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }
  public int getDeliveryAttempts() { return deliveryAttempts; }
  public void setDeliveryAttempts(int deliveryAttempts) { this.deliveryAttempts = deliveryAttempts; }
  public Instant getLastAttemptAt() { return lastAttemptAt; }
  public void setLastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
  public Instant getReadAt() { return readAt; }
  public void setReadAt(Instant readAt) { this.readAt = readAt; }
  public void setCreatedAt(Instant createdAt) { restoreCreatedAt(createdAt); }
}
