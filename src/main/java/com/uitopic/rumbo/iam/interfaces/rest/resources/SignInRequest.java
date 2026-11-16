package com.uitopic.rumbo.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/v1/auth/sign-in}. */
public record SignInRequest(@NotBlank String email, @NotBlank String password) {
  @Override
  public String toString() {
    return "SignInRequest[email=" + email + "]";
  }
}
