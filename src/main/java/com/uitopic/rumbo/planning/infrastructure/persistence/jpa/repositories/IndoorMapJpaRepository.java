package com.uitopic.rumbo.planning.infrastructure.persistence.jpa.repositories;

import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.IndoorMapJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the IndoorMap aggregate's root row. */
public interface IndoorMapJpaRepository extends JpaRepository<IndoorMapJpaEntity, UUID> {}
