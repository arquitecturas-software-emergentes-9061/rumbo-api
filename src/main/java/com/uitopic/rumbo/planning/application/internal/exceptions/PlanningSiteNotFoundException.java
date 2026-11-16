package com.uitopic.rumbo.planning.application.internal.exceptions;

/** The requested site or beacon is unavailable to the caller. */
public class PlanningSiteNotFoundException extends RuntimeException {
  public PlanningSiteNotFoundException() { super("Site or beacon not found"); }
}
