package com.uitopic.rumbo.subscriptions.interfaces.rest;

import com.uitopic.rumbo.subscriptions.application.queryhandlers.GetPlansQueryHandler;
import com.uitopic.rumbo.subscriptions.domain.model.queries.GetPlansQuery;
import com.uitopic.rumbo.subscriptions.interfaces.rest.resources.PlanResponse;
import com.uitopic.rumbo.subscriptions.interfaces.rest.transform.PlanResourceAssembler;
import com.uitopic.rumbo.shared.infrastructure.documentation.openapi.configuration.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/subscriptions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Subscriptions", description = "Monthly subscription plan catalog")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class SubscriptionsPlansController {
  private final GetPlansQueryHandler getPlans;

  public SubscriptionsPlansController(GetPlansQueryHandler getPlans) { this.getPlans = getPlans; }

  @GetMapping("/plans")
  @PreAuthorize("hasRole('CLINIC_ADMIN')")
  @Operation(summary = "List active monthly subscription plans in display order")
  public List<PlanResponse> plans() {
    return getPlans.handle(new GetPlansQuery()).stream().map(PlanResourceAssembler::toResource).toList();
  }
}
