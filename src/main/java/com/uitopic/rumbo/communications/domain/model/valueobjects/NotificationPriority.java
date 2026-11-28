package com.uitopic.rumbo.communications.domain.model.valueobjects;

/** How prominently a client shows a notification. */
public enum NotificationPriority {
  NORMAL,
  /** Something that can affect the operation of the clinic, such as a reported problem on a route. */
  HIGH
}
