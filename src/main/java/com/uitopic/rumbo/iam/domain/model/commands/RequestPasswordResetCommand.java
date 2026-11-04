package com.uitopic.rumbo.iam.domain.model.commands;

/**
 * Request to send a recovery link to an email (US-28). The email may not belong to any account.
 *
 * @param email address typed by the user
 */
public record RequestPasswordResetCommand(String email) {
}
