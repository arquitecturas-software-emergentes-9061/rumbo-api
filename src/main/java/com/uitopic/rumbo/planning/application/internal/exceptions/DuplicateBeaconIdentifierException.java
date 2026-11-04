package com.uitopic.rumbo.planning.application.internal.exceptions;

/** A beacon identifier is already registered within this site. */
public class DuplicateBeaconIdentifierException extends RuntimeException {
  public DuplicateBeaconIdentifierException() { super("The beacon identifier is already registered in this site"); }
}
