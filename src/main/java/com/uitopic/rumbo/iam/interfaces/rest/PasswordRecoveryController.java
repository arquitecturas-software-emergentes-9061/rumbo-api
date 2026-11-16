package com.uitopic.rumbo.iam.interfaces.rest;

import com.uitopic.rumbo.iam.application.commandhandlers.RequestPasswordResetCommandHandler;
import com.uitopic.rumbo.iam.application.commandhandlers.ResetPasswordCommandHandler;
import com.uitopic.rumbo.iam.domain.model.commands.RequestPasswordResetCommand;
import com.uitopic.rumbo.iam.domain.model.commands.ResetPasswordCommand;
import com.uitopic.rumbo.iam.interfaces.rest.resources.PasswordResetConfirmationRequest;
import com.uitopic.rumbo.iam.interfaces.rest.resources.PasswordResetRequest;
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

/** Recovery of a forgotten password (US-28). Both operations are public: the user cannot sign in yet. */
@RestController
@RequestMapping(value = "/api/v1/password-recovery", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Password recovery", description = "Recover access to an account through an emailed link")
public class PasswordRecoveryController {
  private final RequestPasswordResetCommandHandler requestReset;
  private final ResetPasswordCommandHandler resetPassword;

  public PasswordRecoveryController(RequestPasswordResetCommandHandler requestReset,
                                    ResetPasswordCommandHandler resetPassword) {
    this.requestReset = requestReset;
    this.resetPassword = resetPassword;
  }

  @PostMapping("/requests")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Send a recovery link; always 202 so it never reveals whether the email is registered")
  public void request(@Valid @RequestBody PasswordResetRequest request) {
    requestReset.handle(new RequestPasswordResetCommand(request.email()));
  }

  @PostMapping("/confirmations")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Set a new password with the link; the link works once and every session ends")
  public void confirm(@Valid @RequestBody PasswordResetConfirmationRequest request) {
    resetPassword.handle(new ResetPasswordCommand(request.resetToken(), request.newPassword()));
  }
}
