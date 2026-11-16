package com.uitopic.rumbo.planning.application.internal.exceptions;

/** Raised when a requested floor-plan version does not exist for the given floor of the site. */
public final class FirstDraftPlanNotFoundException extends RuntimeException {
  public FirstDraftPlanNotFoundException() {
    super("A floor-plan version does not belong to the site or its floor");
  }
}
