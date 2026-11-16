package com.uitopic.rumbo.iam.interfaces.rest;

import com.uitopic.rumbo.iam.application.commandhandlers.RefreshSessionCommandHandler;
import com.uitopic.rumbo.iam.application.commandhandlers.SignInCommandHandler;
import com.uitopic.rumbo.iam.application.commandhandlers.SignOutCommandHandler;
import com.uitopic.rumbo.iam.application.commandhandlers.SignUpCommandHandler;
import com.uitopic.rumbo.iam.domain.model.commands.RefreshSessionCommand;
import com.uitopic.rumbo.iam.domain.model.commands.SignOutCommand;
import com.uitopic.rumbo.iam.domain.model.valueobjects.SessionPolicy;
import com.uitopic.rumbo.iam.interfaces.rest.resources.AuthenticatedUserResponse;
import com.uitopic.rumbo.iam.interfaces.rest.resources.RefreshSessionRequest;
import com.uitopic.rumbo.iam.interfaces.rest.resources.SignInRequest;
import com.uitopic.rumbo.iam.interfaces.rest.resources.SignUpRequest;
import com.uitopic.rumbo.iam.interfaces.rest.resources.UserAccountResponse;
import com.uitopic.rumbo.iam.interfaces.rest.transform.IamResourceAssembler;
import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.shared.infrastructure.documentation.openapi.configuration.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication", description = "Sign-up, sign-in and session credentials")
public class AuthenticationController {
  private final SignUpCommandHandler signUp;
  private final SignInCommandHandler signIn;
  private final RefreshSessionCommandHandler refresh;
  private final SignOutCommandHandler signOut;
  private final SessionPolicy sessionPolicy;

  public AuthenticationController(SignUpCommandHandler signUp, SignInCommandHandler signIn,
                                  RefreshSessionCommandHandler refresh, SignOutCommandHandler signOut,
                                  SessionPolicy sessionPolicy) {
    this.signUp = signUp;
    this.signIn = signIn;
    this.refresh = refresh;
    this.signOut = signOut;
    this.sessionPolicy = sessionPolicy;
  }

  @PostMapping("/sign-up")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Register an account (TS-26)")
  public UserAccountResponse signUp(@Valid @RequestBody SignUpRequest request) {
    return IamResourceAssembler.toResponse(signUp.handle(IamResourceAssembler.toCommand(request)));
  }

  @PostMapping("/sign-in")
  @Operation(summary = "Verify credentials and open a session (TS-01)")
  public AuthenticatedUserResponse signIn(@Valid @RequestBody SignInRequest request) {
    return IamResourceAssembler.toResponse(signIn.handle(IamResourceAssembler.toCommand(request)),
      sessionPolicy.accessTokenLifetime());
  }

  @PostMapping("/refresh")
  @Operation(summary = "Rotate the session: the refresh token works once and a new one is returned (TS-32)")
  public AuthenticatedUserResponse refresh(@Valid @RequestBody RefreshSessionRequest request) {
    return IamResourceAssembler.toResponse(refresh.handle(new RefreshSessionCommand(request.refreshToken())),
      sessionPolicy.accessTokenLifetime());
  }

  @PostMapping("/sign-out")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
  @Operation(summary = "End the current session; its tokens stop working at once (TS-32)")
  public void signOut(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    signOut.handle(new SignOutCommand(principal.userId(), principal.sessionId()));
  }
}
