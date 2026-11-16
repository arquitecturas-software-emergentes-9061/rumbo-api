package com.uitopic.rumbo.iam.domain.exceptions;

import java.time.Instant;

/** The account is locked after too many failed attempts; the password is not even checked while the lock lasts. */
public class AccountLockedException extends IamException {
  private final Instant lockedUntil;

  public AccountLockedException(Instant lockedUntil) {
    super("ACCOUNT_LOCKED", "The account is temporarily locked after too many failed attempts");
    this.lockedUntil = lockedUntil;
  }

  public Instant lockedUntil() { return lockedUntil; }
}
