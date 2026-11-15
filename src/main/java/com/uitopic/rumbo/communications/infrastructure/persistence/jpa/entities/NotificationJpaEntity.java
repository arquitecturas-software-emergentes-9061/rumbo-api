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
 * aggregate. Columns must stay aligned with {@code V202611151100__communications_create_notifications.sql}.
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

  @Column(name = "delivery_status", nullable = false, length = 20)
  private String deliveryStatus;

  @Column(name = "delivery_attempts", nullable = false)
  private int deliveryAttempts;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

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
