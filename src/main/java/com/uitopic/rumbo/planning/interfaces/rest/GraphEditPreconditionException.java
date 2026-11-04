package com.uitopic.rumbo.planning.interfaces.rest;

/** Missing or malformed HTTP precondition for replacing a draft graph. */
public final class GraphEditPreconditionException extends RuntimeException {
  private final boolean missing;

  public GraphEditPreconditionException(boolean missing) {
    super(missing ? "If-Match is required" : "If-Match must contain one draft graph ETag");
    this.missing = missing;
  }

  public boolean missing() { return missing; }
}
