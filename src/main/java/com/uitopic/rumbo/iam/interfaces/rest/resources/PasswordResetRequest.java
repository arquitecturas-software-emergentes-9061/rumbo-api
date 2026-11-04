package com.uitopic.rumbo.iam.interfaces.rest.resources;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/password-recovery/requests}. The format of the email is not validated here: a malformed
 * address gets the same neutral answer as an unknown one.
 */
public record PasswordResetRequest(@NotBlank @Size(max = 254) String email) {
}
