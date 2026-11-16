package com.uitopic.rumbo.planning.application.internal.exceptions;

/** A beacon point does not lie inside a registered plan of its site and floor. */
public class InvalidBeaconAnchorException extends RuntimeException {
  public InvalidBeaconAnchorException() { super("Beacon location does not belong to a registered floor-plan version"); }
}
