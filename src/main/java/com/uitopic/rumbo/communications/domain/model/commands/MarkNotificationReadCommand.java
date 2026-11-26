package com.uitopic.rumbo.communications.domain.model.commands;

import java.util.UUID;

public record MarkNotificationReadCommand(UUID userId, UUID notificationId) {
}
