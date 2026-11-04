package com.uitopic.rumbo.iam.domain.model.valueobjects;

/** Lifecycle states of an account. Only ACTIVE accounts, or LOCKED ones whose lock expired, can sign in. */
public enum AccountStatus {
  ACTIVE,
  LOCKED,
  DISABLED
}
