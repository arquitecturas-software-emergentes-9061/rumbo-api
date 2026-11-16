package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.planning.domain.model.entities.Site;
import com.uitopic.rumbo.planning.domain.services.SiteCommandService;
import com.uitopic.rumbo.planning.domain.services.SiteQueryService;
import com.uitopic.rumbo.shared.infrastructure.documentation.openapi.configuration.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Planning entry point for sites owned by the authenticated administrator. */
@RestController
@RequestMapping("/api/v1/sites")
@Tag(name = "Sites")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@PreAuthorize("hasRole('CLINIC_ADMIN')")
public class SitesController {
  private final SiteCommandService commands;
  private final SiteQueryService queries;

  public SitesController(SiteCommandService commands, SiteQueryService queries) {
    this.commands = commands;
    this.queries = queries;
  }

  /** Request to register a clinic site. */
  public record CreateSiteRequest(@NotBlank @Size(min=2,max=120) String name,
      @Size(max=200) String address, boolean allowDuplicateAddress) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Site create(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                     @Valid @RequestBody CreateSiteRequest request) {
    return commands.create(principal.userId(), request.name(), request.address(), request.allowDuplicateAddress());
  }

  @PutMapping("/{siteId}")
  public Site update(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID siteId,
                     @Valid @RequestBody CreateSiteRequest request) {
    return commands.update(principal.userId(), siteId, request.name(), request.address(), request.allowDuplicateAddress());
  }

  @GetMapping
  public List<Site> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return queries.findByOwner(principal.userId());
  }
}
