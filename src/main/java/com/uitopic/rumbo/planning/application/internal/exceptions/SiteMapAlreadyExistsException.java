package com.uitopic.rumbo.planning.application.internal.exceptions;

/** Raised when a site already owns an indoor map, so its first draft cannot be created again. */
public final class SiteMapAlreadyExistsException extends RuntimeException {
  public SiteMapAlreadyExistsException() {
    super("The site already has an indoor map; create a new version from its published map instead");
  }
}
