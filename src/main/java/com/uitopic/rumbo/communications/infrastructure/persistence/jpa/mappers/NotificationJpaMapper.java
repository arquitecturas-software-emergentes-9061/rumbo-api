package com.uitopic.rumbo.communications.infrastructure.persistence.jpa.mappers;

import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.valueobjects.DeliveryStatus;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationChannel;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationPriority;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;
import com.uitopic.rumbo.communications.infrastructure.persistence.jpa.entities.NotificationJpaEntity;

/** Converts between the {@link Notification} aggregate and its persistence entity. */
public final class NotificationJpaMapper {
  private NotificationJpaMapper() {}

  public static NotificationJpaEntity toEntity(Notification notification) {
    NotificationJpaEntity entity = new NotificationJpaEntity();
    entity.setId(notification.id());
    entity.setRecipientUserId(notification.recipientUserId());
    entity.setType(notification.type().name());
    entity.setChannel(notification.channel().name());
    entity.setDetail(notification.detail());
    entity.setSiteId(notification.siteId());
    entity.setSourceEventId(notification.sourceEventId());
    entity.setPriority(notification.priority().name());
    entity.setNextAttemptAt(notification.nextAttemptAt());
    entity.setDeliveryStatus(notification.deliveryStatus().name());
    entity.setDeliveryAttempts(notification.deliveryAttempts());
    entity.setLastAttemptAt(notification.lastAttemptAt());
    entity.setReadAt(notification.readAt());
    entity.setCreatedAt(notification.createdAt());
    return entity;
  }

  public static Notification toDomain(NotificationJpaEntity entity) {
    return new Notification(entity.getId(), entity.getRecipientUserId(), NotificationType.valueOf(entity.getType()),
        NotificationChannel.valueOf(entity.getChannel()), entity.getDetail(), entity.getSiteId(),
        entity.getSourceEventId(), NotificationPriority.valueOf(entity.getPriority()),
        DeliveryStatus.valueOf(entity.getDeliveryStatus()), entity.getDeliveryAttempts(), entity.getLastAttemptAt(),
        entity.getNextAttemptAt(), entity.getReadAt(), entity.getCreatedAt(), entity.getUpdatedAt());
  }
}
