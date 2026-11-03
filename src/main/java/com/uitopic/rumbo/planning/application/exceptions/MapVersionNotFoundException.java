package com.uitopic.rumbo.planning.application.exceptions;

/** The requested site, map, and version combination does not exist. */
public final class MapVersionNotFoundException extends RuntimeException {
  public MapVersionNotFoundException() {
    super("Indoor map version was not found for this site");
  }
}
