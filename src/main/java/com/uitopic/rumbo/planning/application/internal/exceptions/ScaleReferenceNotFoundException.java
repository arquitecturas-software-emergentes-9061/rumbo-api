package com.uitopic.rumbo.planning.application.internal.exceptions;

/** The draft plan or its stored calibration is unavailable to the caller. */
public class ScaleReferenceNotFoundException extends RuntimeException {
  public ScaleReferenceNotFoundException() { super("Plan scale or site not found"); }
}
