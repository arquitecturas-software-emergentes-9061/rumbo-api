package com.uitopic.rumbo.shared.infrastructure.persistence.jpa;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Enables {@code @CreatedDate} and {@code @LastModifiedDate} on persistence entities of every bounded context. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfiguration {
}
