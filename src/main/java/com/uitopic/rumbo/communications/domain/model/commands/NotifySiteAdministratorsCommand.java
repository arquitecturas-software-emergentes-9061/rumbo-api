package com.uitopic.rumbo.communications.domain.model.commands;

import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationPriority;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;
import java.util.Objects;
import java.util.UUID;

/**
 * Tell the administrators of a site about an event of its operation (US-32).
 *
 * @param sourceEventId id of the event, so that the same event notifies each administrator once
 */
public record NotifySiteAdministratorsCommand(UUID siteId, NotificationType type, String detail, UUID sourceEventId,
                                              NotificationPriority priority) {
  public NotifySiteAdministratorsCommand {
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(type, "type is required");
    Objects.requireNonNull(sourceEventId, "sourceEventId is required");
    Objects.requireNonNull(priority, "priority is required");
    if (!type.isOperational()) throw new IllegalArgumentException(type + " is not about the operation of a site");
  }
}
