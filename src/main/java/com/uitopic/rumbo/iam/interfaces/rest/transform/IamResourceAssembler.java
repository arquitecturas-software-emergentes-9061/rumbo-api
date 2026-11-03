package com.uitopic.rumbo.iam.interfaces.rest.transform;

import com.uitopic.rumbo.iam.application.results.AuthenticatedUser;
import com.uitopic.rumbo.iam.domain.model.aggregates.UserAccount;
import com.uitopic.rumbo.iam.domain.model.commands.SignInCommand;
import com.uitopic.rumbo.iam.domain.model.commands.SignUpCommand;
import com.uitopic.rumbo.iam.interfaces.rest.resources.AuthenticatedUserResponse;
import com.uitopic.rumbo.iam.interfaces.rest.resources.SignInRequest;
import com.uitopic.rumbo.iam.interfaces.rest.resources.SignUpRequest;
import com.uitopic.rumbo.iam.interfaces.rest.resources.UserAccountResponse;

import java.time.Duration;

/** Converts REST resources into commands and domain results into REST resources. */
public final class IamResourceAssembler {
  private IamResourceAssembler() {}

  public static SignUpCommand toCommand(SignUpRequest request) {
    return new SignUpCommand(request.email(), request.password(), request.role(), request.fullName(),
      request.clinicName());
  }

  public static SignInCommand toCommand(SignInRequest request) {
    return new SignInCommand(request.email(), request.password());
  }

  public static UserAccountResponse toResponse(UserAccount account) {
    return new UserAccountResponse(account.id(), account.email().value(), account.role().name(),
      account.status().name(), account.clinicId().orElse(null), account.createdAt());
  }

  public static AuthenticatedUserResponse toResponse(AuthenticatedUser user, Duration accessTokenLifetime) {
    return new AuthenticatedUserResponse(user.userId(), user.role().name(), user.clinicId(), "Bearer",
      user.accessToken(), accessTokenLifetime.toSeconds(), user.refreshToken(), user.refreshTokenExpiresAt());
  }
}
