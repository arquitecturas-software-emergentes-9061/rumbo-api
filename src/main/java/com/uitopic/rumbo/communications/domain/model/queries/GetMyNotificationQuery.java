package com.uitopic.rumbo.communications.domain.model.queries;

import java.util.UUID;

public record GetMyNotificationQuery(UUID userId, UUID notificationId) {
}
