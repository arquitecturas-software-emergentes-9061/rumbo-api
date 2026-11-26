package com.uitopic.rumbo.communications.domain.exceptions;

/** The notification does not exist or belongs to another account; both look the same to the caller. */
public class NotificationNotFoundException extends CommunicationsException {
  public static final String CODE = "NOTIFICATION_NOT_FOUND";

  public NotificationNotFoundException() { super(CODE, "Notification was not found"); }
}
