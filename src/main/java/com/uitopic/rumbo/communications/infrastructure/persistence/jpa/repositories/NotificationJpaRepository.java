package com.uitopic.rumbo.communications.infrastructure.persistence.jpa.repositories;

import com.uitopic.rumbo.communications.infrastructure.persistence.jpa.entities.NotificationJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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

  @Query("select n from NotificationJpaEntity n where n.recipientUserId = :recipient"
      + " and (:unreadOnly = false or n.readAt is null)"
      + " and (:siteId is null or n.siteId = :siteId)"
      + " and (:type is null or n.type = :type)"
      + " and (:priority is null or n.priority = :priority)")
  Page<NotificationJpaEntity> search(@Param("recipient") UUID recipientUserId, @Param("unreadOnly") boolean unreadOnly,
      @Param("siteId") UUID siteId, @Param("type") String type, @Param("priority") String priority,
      Pageable pageable);

  boolean existsBySourceEventIdAndRecipientUserId(UUID sourceEventId, UUID recipientUserId);

  @Query(value = "SELECT * FROM communications_notifications"
      + " WHERE delivery_status IN ('PENDING', 'RETRY_PENDING') AND next_attempt_at <= :now"
      + " ORDER BY next_attempt_at, id LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
  List<NotificationJpaEntity> lockDue(@Param("now") Instant now, @Param("limit") int limit);
}
