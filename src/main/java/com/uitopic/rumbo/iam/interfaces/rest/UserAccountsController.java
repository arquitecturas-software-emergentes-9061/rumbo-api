package com.uitopic.rumbo.iam.interfaces.rest;

import com.uitopic.rumbo.iam.application.commandhandlers.SignOutCommandHandler;
import com.uitopic.rumbo.iam.application.queryhandlers.GetActiveSessionsQueryHandler;
import com.uitopic.rumbo.iam.application.queryhandlers.GetUserAccountByIdQueryHandler;
import com.uitopic.rumbo.iam.domain.model.commands.SignOutCommand;
import com.uitopic.rumbo.iam.domain.model.queries.GetActiveSessionsQuery;
import com.uitopic.rumbo.iam.domain.model.queries.GetUserAccountByIdQuery;
import com.uitopic.rumbo.iam.interfaces.rest.resources.SessionResponse;
import com.uitopic.rumbo.iam.interfaces.rest.resources.UserAccountResponse;
import com.uitopic.rumbo.iam.interfaces.rest.transform.IamResourceAssembler;
import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.shared.interfaces.rest.openapi.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/user-accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "User accounts", description = "Account and sessions of the authenticated user")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class UserAccountsController {
  private final GetUserAccountByIdQueryHandler getUserAccount;
  private final GetActiveSessionsQueryHandler getActiveSessions;
  private final SignOutCommandHandler signOut;

  public UserAccountsController(GetUserAccountByIdQueryHandler getUserAccount,
                                GetActiveSessionsQueryHandler getActiveSessions, SignOutCommandHandler signOut) {
    this.getUserAccount = getUserAccount;
    this.getActiveSessions = getActiveSessions;
    this.signOut = signOut;
  }

  @GetMapping("/me")
  @Operation(summary = "Account of the caller, identified by the access token")
  public UserAccountResponse me(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return IamResourceAssembler.toResponse(getUserAccount.handle(new GetUserAccountByIdQuery(principal.userId())));
  }

  @GetMapping("/me/sessions")
  @Operation(summary = "Active sessions of the caller, newest first (TS-32)")
  public List<SessionResponse> sessions(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return getActiveSessions.handle(new GetActiveSessionsQuery(principal.userId())).stream()
      .map(session -> IamResourceAssembler.toResponse(session, principal.sessionId()))
      .toList();
  }

  @DeleteMapping("/me/sessions/{sessionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "End one of the caller's sessions, for example on a lost device (TS-32)")
  public void endSession(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID sessionId) {
    signOut.handle(new SignOutCommand(principal.userId(), sessionId));
  }
}
