package com.uitopic.rumbo.planning.application.internal.exceptions;

/** A beacon identifier or physical iBeacon identity is already registered. */
public class DuplicateBeaconIdentifierException extends RuntimeException {
  public DuplicateBeaconIdentifierException() { super("The beacon identity is already registered"); }
}
