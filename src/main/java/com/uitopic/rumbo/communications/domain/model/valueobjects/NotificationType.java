package com.uitopic.rumbo.communications.domain.model.valueobjects;

/**
 * Event that produced a notification. Clients localize the text from the type; {@code detail} carries the only
 * variable datum (for example the plan code), never prose.
 */
public enum NotificationType {
  /** A new account was registered. */
  WELCOME,
  /** The account reached the failed sign-in threshold; {@code detail} is the unlock instant. */
  ACCOUNT_LOCKED,
  /** The password of the account changed. */
  PASSWORD_CHANGED,
  /** A paid subscription became active; {@code detail} is the plan code. */
  SUBSCRIPTION_ACTIVATED
}
