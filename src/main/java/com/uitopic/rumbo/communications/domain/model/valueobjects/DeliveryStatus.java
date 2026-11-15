package com.uitopic.rumbo.communications.domain.model.valueobjects;

/** Delivery state of a notification on its channel. */
public enum DeliveryStatus {
  /** No delivery attempt has been made yet. */
  PENDING,
  /** Delivered successfully. Final. */
  SENT,
  /** An attempt failed and another one will be made. */
  RETRY_PENDING,
  /** Every attempt failed. Final. */
  FAILED;

  public boolean isFinal() { return this == SENT || this == FAILED; }
}
