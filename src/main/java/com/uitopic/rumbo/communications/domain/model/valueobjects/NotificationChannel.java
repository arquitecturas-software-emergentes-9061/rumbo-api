package com.uitopic.rumbo.communications.domain.model.valueobjects;

/** Medium through which a notification reaches its recipient. Every notification is listed in the center. */
public enum NotificationChannel {
  /** Shown in the notification center of the web and mobile apps; delivered when it is stored. */
  IN_APP,
  /** Also shown in the center and sent by email; delivered once the email is accepted, with retries. */
  EMAIL
}
