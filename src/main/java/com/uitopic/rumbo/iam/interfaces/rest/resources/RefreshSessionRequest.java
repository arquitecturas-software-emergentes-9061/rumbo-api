package com.uitopic.rumbo.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/v1/auth/refresh}. */
public record RefreshSessionRequest(@NotBlank String refreshToken) {
  @Override
  public String toString() {
    return "RefreshSessionRequest[refreshToken=***]";
  }
}
