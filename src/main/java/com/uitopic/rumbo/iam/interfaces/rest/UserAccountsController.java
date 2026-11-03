package com.uitopic.rumbo.iam.interfaces.rest;

import com.uitopic.rumbo.iam.application.queryhandlers.GetUserAccountByIdQueryHandler;
import com.uitopic.rumbo.iam.domain.model.queries.GetUserAccountByIdQuery;
import com.uitopic.rumbo.iam.interfaces.rest.resources.UserAccountResponse;
import com.uitopic.rumbo.iam.interfaces.rest.transform.IamResourceAssembler;
import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.shared.interfaces.rest.openapi.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/user-accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "User accounts", description = "Account of the authenticated user")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class UserAccountsController {
  private final GetUserAccountByIdQueryHandler getUserAccount;

  public UserAccountsController(GetUserAccountByIdQueryHandler getUserAccount) {
    this.getUserAccount = getUserAccount;
  }

  @GetMapping("/me")
  @Operation(summary = "Account of the caller, identified by the access token")
  public UserAccountResponse me(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return IamResourceAssembler.toResponse(getUserAccount.handle(new GetUserAccountByIdQuery(principal.userId())));
  }
}
