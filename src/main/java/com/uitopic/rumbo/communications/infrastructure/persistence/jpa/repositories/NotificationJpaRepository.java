package com.uitopic.rumbo.communications.infrastructure.persistence.jpa.repositories;

import com.uitopic.rumbo.communications.infrastructure.persistence.jpa.entities.NotificationJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Spring Data access to {@code communications_notifications}. */
public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {
  Optional<NotificationJpaEntity> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

  Page<NotificationJpaEntity> findByRecipientUserId(UUID recipientUserId, Pageable pageable);

  Page<NotificationJpaEntity> findByRecipientUserIdAndReadAtIsNull(UUID recipientUserId, Pageable pageable);

  long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update NotificationJpaEntity n set n.readAt = :now, n.updatedAt = :now "
      + "where n.recipientUserId = :recipientUserId and n.readAt is null")
  int markAllRead(@Param("recipientUserId") UUID recipientUserId, @Param("now") Instant now);
}
