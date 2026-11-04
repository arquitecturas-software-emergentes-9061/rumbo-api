package com.uitopic.rumbo.navigation.interfaces.rest;

import com.uitopic.rumbo.shared.infrastructure.documentation.openapi.configuration.OpenApiConfiguration;
import com.uitopic.rumbo.navigation.application.internal.exceptions.RouteNotFoundException;
import com.uitopic.rumbo.navigation.domain.services.PublishedRouteQueryService;
import com.uitopic.rumbo.navigation.interfaces.rest.resources.PublishedRouteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controlled route API over the current published version of a site. */
@RestController
@PreAuthorize("hasRole('CLINIC_ADMIN') and @siteOwnerGuard.isOwner(authentication, #siteId)")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@Tag(name = "Published routes")
@Profile("postgres")
@ConditionalOnProperty(name = "rumbo.navigation.routes.enabled", havingValue = "true")
@RequestMapping("/api/v1/sites/{siteId}/routes")
public class PublishedRoutesController {
  private final PublishedRouteQueryService routes;

  public PublishedRoutesController(PublishedRouteQueryService routes) {
    this.routes = routes;
  }

  /** Finds an ordered route between two nodes of the current published model. */
  @Operation(summary = "Calculate an indoor route for a site",
      description = "Uses the current PUBLISHED model and identifies cross-floor traversals "
          + "in verticalSections. accessibleOnly excludes connections without confirmed "
          + "accessibility. Disabled until site authorization is available.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Route found",
          content = @Content(schema = @Schema(implementation = PublishedRouteResponse.class),
              examples = @ExampleObject(value = """
                  {"siteId":"00000000-0000-4000-8000-000000000001",
                   "modelVersionId":"00000000-0000-4000-8000-000000000002",
                   "totalDistanceMeters":5.0,"totalCost":10.0,
                   "nodes":[
                     {"nodeId":"00000000-0000-4000-8000-000000000010",
                      "floorId":"00000000-0000-4000-8000-000000000020",
                      "planVersionId":"00000000-0000-4000-8000-000000000030",
                      "x":1.0,"y":2.0,"role":"ACCESS"},
                     {"nodeId":"00000000-0000-4000-8000-000000000011",
                      "floorId":"00000000-0000-4000-8000-000000000021",
                      "planVersionId":"00000000-0000-4000-8000-000000000031",
                      "x":2.0,"y":2.0,"role":"DESTINATION"}],
                   "links":[{"linkId":"00000000-0000-4000-8000-000000000040",
                     "fromNodeId":"00000000-0000-4000-8000-000000000010",
                     "toNodeId":"00000000-0000-4000-8000-000000000011",
                     "type":"ELEVATOR","distanceMeters":5.0,"weight":7.0,
                     "verticalPenalty":3.0,"accessibility":"ACCESSIBLE"}],
                   "verticalSections":[{"linkIndex":0,
                     "linkId":"00000000-0000-4000-8000-000000000040",
                     "fromNodeId":"00000000-0000-4000-8000-000000000010",
                     "toNodeId":"00000000-0000-4000-8000-000000000011",
                     "fromFloorId":"00000000-0000-4000-8000-000000000020",
                     "toFloorId":"00000000-0000-4000-8000-000000000021",
                     "type":"ELEVATOR","distanceMeters":5.0,"cost":10.0}]}
                  """))),
      @ApiResponse(responseCode = "400", description = "Invalid parameters or another site's node"),
      @ApiResponse(responseCode = "404", description = "Published model, node, or route missing"),
      @ApiResponse(responseCode = "422", description = "Published model cannot be routed")
  })
  @GetMapping
  public ResponseEntity<PublishedRouteResponse> find(
      @Parameter(description = "Site whose current published model is used")
      @PathVariable UUID siteId,
      @Parameter(description = "Origin node ID") @RequestParam(name = "from") UUID fromNodeId,
      @Parameter(description = "Destination node ID") @RequestParam(name = "to") UUID toNodeId,
      @Parameter(description = "Require confirmed accessibility on every link")
      @RequestParam(defaultValue = "false") boolean accessibleOnly) {
    return ResponseEntity.ok(PublishedRouteResponse.from(routes.calculate(siteId,
        fromNodeId, toNodeId, accessibleOnly).orElseThrow(RouteNotFoundException::new)));
  }
}
