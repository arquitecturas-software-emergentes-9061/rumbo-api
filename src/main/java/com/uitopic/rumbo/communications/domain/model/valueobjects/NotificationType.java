package com.uitopic.rumbo.communications.domain.model.valueobjects;

/**
 * Event that produced a notification. Clients may localize the text from the type; {@code detail} carries the only
 * variable datum (for example the plan code), never prose. The API also renders a title and a message for it.
 */
public enum NotificationType {
  /** A new account was registered. */
  WELCOME,
  /** The account reached the failed sign-in threshold; {@code detail} is the unlock instant. */
  ACCOUNT_LOCKED,
  /** The password of the account changed. */
  PASSWORD_CHANGED,
  /** A paid subscription became active; {@code detail} is the plan code. */
  SUBSCRIPTION_ACTIVATED,
  /** A visitor reported a problem on a route of the site; {@code detail} is the incident category. */
  INCIDENT_REPORTED,
  /** A new version of the map of the site was published; {@code detail} is the id of the published version. */
  MAP_PUBLISHED;

  /** Whether the type concerns the operation of a site rather than the account of the recipient. */
  public boolean isOperational() {
    return this == INCIDENT_REPORTED || this == MAP_PUBLISHED;
  }
}
