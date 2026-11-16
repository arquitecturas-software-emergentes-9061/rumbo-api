package com.uitopic.rumbo.shared.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base class for persistence entities that keep {@code created_at} and {@code updated_at} columns.
 * Only persistence entities extend it; domain aggregates stay free of JPA annotations.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableJpaEntity {
  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Instant getCreatedAt() { return createdAt; }

  public Instant getUpdatedAt() { return updatedAt; }

  /** Keeps the creation instant decided by the domain when an existing aggregate is mapped back. */
  protected void restoreCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
