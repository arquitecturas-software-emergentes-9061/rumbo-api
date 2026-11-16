package com.uitopic.rumbo.planning.application.internal.exceptions;

/** Raised when the selected source is no longer the current published version. */
public final class PublishedMapVersionChangedException extends RuntimeException {
  public PublishedMapVersionChangedException() {
    super("The selected source is not the current published map version");
  }
}
