package com.uitopic.rumbo.communications.infrastructure.persistence.jpa.adapters;

import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationSlice;
import com.uitopic.rumbo.communications.domain.repositories.NotificationRepository;
import com.uitopic.rumbo.communications.infrastructure.persistence.jpa.entities.NotificationJpaEntity;
import com.uitopic.rumbo.communications.infrastructure.persistence.jpa.mappers.NotificationJpaMapper;
import com.uitopic.rumbo.communications.infrastructure.persistence.jpa.repositories.NotificationJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** JPA implementation of {@link NotificationRepository}. */
@Repository
public class NotificationRepositoryImpl implements NotificationRepository {
  private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

  private final NotificationJpaRepository jpa;

  public NotificationRepositoryImpl(NotificationJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Notification save(Notification notification) {
    return NotificationJpaMapper.toDomain(jpa.saveAndFlush(NotificationJpaMapper.toEntity(notification)));
  }

  @Override
  public Optional<Notification> findByIdAndRecipient(UUID id, UUID recipientUserId) {
    return jpa.findByIdAndRecipientUserId(id, recipientUserId).map(NotificationJpaMapper::toDomain);
  }

  @Override
  public NotificationSlice findPage(UUID recipientUserId, boolean unreadOnly, int page, int size) {
    PageRequest paging = PageRequest.of(page, size, NEWEST_FIRST);
    Page<NotificationJpaEntity> rows = unreadOnly
        ? jpa.findByRecipientUserIdAndReadAtIsNull(recipientUserId, paging)
        : jpa.findByRecipientUserId(recipientUserId, paging);
    return new NotificationSlice(rows.getContent().stream().map(NotificationJpaMapper::toDomain).toList(),
        rows.getTotalElements());
  }

  @Override
  public long countUnread(UUID recipientUserId) {
    return jpa.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
  }

  @Override
  public int markAllRead(UUID recipientUserId, Instant now) {
    return jpa.markAllRead(recipientUserId, now);
  }
}
