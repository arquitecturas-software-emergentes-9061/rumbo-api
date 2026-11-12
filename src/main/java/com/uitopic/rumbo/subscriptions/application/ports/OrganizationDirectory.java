package com.uitopic.rumbo.subscriptions.application.ports;

import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.OrganizationRef;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationDirectory {
  Optional<OrganizationRef> findOrganizationOf(UUID userId);
}
