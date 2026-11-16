package com.uitopic.rumbo.shared.interfaces.rest.resources;

import java.util.Objects;

/** HTTP error response when no context-specific details are required. */
public record ApiErrorResource(String code, String message) implements ApiErrorResponse {
  public ApiErrorResource {
    Objects.requireNonNull(code, "code is required");
    Objects.requireNonNull(message, "message is required");
  }
}
