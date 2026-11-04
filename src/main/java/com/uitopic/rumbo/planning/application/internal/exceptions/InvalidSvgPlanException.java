package com.uitopic.rumbo.planning.application.internal.exceptions;

/** An SVG cannot be interpreted as a safe plan preview. */
public class InvalidSvgPlanException extends RuntimeException {
  public InvalidSvgPlanException(String message) { super(message); }
}
