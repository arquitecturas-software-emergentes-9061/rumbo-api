package com.uitopic.rumbo.planning.application.internal.exceptions;

/** A plan version already has a calibration for another floor. */
public class ScaleReferenceFloorConflictException extends RuntimeException {
  public ScaleReferenceFloorConflictException() { super("Plan version already belongs to a different floor"); }
}
