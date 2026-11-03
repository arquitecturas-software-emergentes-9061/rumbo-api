package com.uitopic.rumbo.iam.interfaces.rest;

import com.uitopic.rumbo.iam.application.commandhandlers.SignInCommandHandler;
import com.uitopic.rumbo.iam.application.commandhandlers.SignUpCommandHandler;
import com.uitopic.rumbo.iam.domain.model.valueobjects.SessionPolicy;
import com.uitopic.rumbo.iam.interfaces.rest.resources.AuthenticatedUserResponse;
import com.uitopic.rumbo.iam.interfaces.rest.resources.SignInRequest;
import com.uitopic.rumbo.iam.interfaces.rest.resources.SignUpRequest;
import com.uitopic.rumbo.iam.interfaces.rest.resources.UserAccountResponse;
import com.uitopic.rumbo.iam.interfaces.rest.transform.IamResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
  private final SessionPolicy sessionPolicy;

  public AuthenticationController(SignUpCommandHandler signUp, SignInCommandHandler signIn,
                                  SessionPolicy sessionPolicy) {
    this.signUp = signUp;
    this.signIn = signIn;
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
}
