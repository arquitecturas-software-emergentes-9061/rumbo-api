package com.uitopic.rumbo.communications.domain.model.commands;

import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;

import java.util.UUID;

public record CreateNotificationCommand(UUID recipientUserId, NotificationType type, String detail) {
}
