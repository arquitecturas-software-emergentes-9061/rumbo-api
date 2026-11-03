package com.uitopic.rumbo.navigation.interfaces;

import com.uitopic.rumbo.navigation.domain.CalculatedRoute;
import com.uitopic.rumbo.navigation.domain.PublishedSpatialGraph;
import com.uitopic.rumbo.navigation.domain.services.PublishedRouteQueryService;
import com.uitopic.rumbo.shared.interfaces.rest.ProblemDetails;
import com.uitopic.rumbo.shared.interfaces.rest.openapi.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * PR-229: real, site-scoped route API over the current published Planning graph.
 * Accessible-only routing fails closed until the published schema models accessibility.
 */
@RestController
@Profile("postgres")
@ConditionalOnProperty(name = "rumbo.navigation.routes.enabled", havingValue = "true")
@PreAuthorize("hasRole('CLINIC_ADMIN') and @siteOwnerGuard.isOwner(authentication, #siteId)")
@RequestMapping("/api/v1/sites/{siteId}/routes")
@Tag(name = "Published routes")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
public class PublishedRouteController {
  private final PublishedRouteQueryService routes;

  public PublishedRouteController(PublishedRouteQueryService routes) { this.routes = routes; }

  public record Node(UUID floorId, UUID nodeId, String role, double x, double y) {}
  public record Link(UUID linkId, UUID fromNodeId, UUID toNodeId, String kind, double distanceMeters,
                     double weight, double verticalPenalty, double cost) {}
  public record RouteResponse(UUID siteId, UUID modelVersionId, double totalDistanceMeters,
                              double totalCost, List<Node> nodes, List<Link> links) {}

  @Operation(summary = "Calculate a weighted A* route in the current published map",
    description = "from/to are node UUIDs of one published site graph. The cost includes per-link routing weight and vertical penalty. " +
      "accessibleOnly=true is rejected until real accessibility metadata exists; never assume a route is accessible.")
  @ApiResponses({
    @ApiResponse(responseCode="200", description="Complete ordered route and its physical and weighted costs",
      content=@Content(mediaType="application/json", examples=@ExampleObject(
        name="Published route", value="{\"siteId\":\"00000000-0000-4000-8000-000000000001\"," +
        "\"modelVersionId\":\"00000000-0000-4000-8000-000000000020\"," +
        "\"totalDistanceMeters\":12.0,\"totalCost\":8.5," +
        "\"nodes\":[{\"floorId\":\"00000000-0000-4000-8000-000000000002\"," +
        "\"nodeId\":\"00000000-0000-4000-8000-000000000010\",\"role\":\"ACCESS\",\"x\":0.0,\"y\":0.0}]," +
        "\"links\":[{\"linkId\":\"00000000-0000-4000-8000-000000000030\"," +
        "\"fromNodeId\":\"00000000-0000-4000-8000-000000000010\"," +
        "\"toNodeId\":\"00000000-0000-4000-8000-000000000011\"," +
        "\"kind\":\"CORRIDOR\",\"distanceMeters\":12.0,\"weight\":8.5," +
        "\"verticalPenalty\":0.0,\"cost\":8.5}]}"))),
    @ApiResponse(responseCode="400", description="Malformed or unknown node IDs"),
    @ApiResponse(responseCode="401", description="Missing credentials"),
    @ApiResponse(responseCode="403", description="Caller has no access to site"),
    @ApiResponse(responseCode="404", description="Published map or connected route not available"),
    @ApiResponse(responseCode="422", description="Accessibility model unavailable or invalid published graph")
  })
  @GetMapping
  public RouteResponse route(@PathVariable UUID siteId,
      @RequestParam(name="from") UUID from, @RequestParam(name="to") UUID to,
      @RequestParam(defaultValue="false") boolean accessibleOnly) {
    if (accessibleOnly) throw new UnsupportedAccessibility();
    CalculatedRoute r = routes.calculate(siteId, from, to).orElseThrow(NoRoute::new);
    return new RouteResponse(r.siteId(), r.modelVersionId(), r.totalDistanceMeters(), r.totalCost(),
      r.nodes().stream().map(n -> new Node(n.floorId(), n.id(), n.role().name(), n.x(), n.y())).toList(),
      r.legs().stream().map(link -> new Link(link.linkId(), link.fromNodeId(), link.toNodeId(),
        link.kind().name(), link.distanceMeters(), link.weight(), link.verticalPenalty(), link.cost())).toList());
  }

  public static final class NoRoute extends RuntimeException {}
  public static final class UnsupportedAccessibility extends RuntimeException {}

  @RestControllerAdvice(assignableTypes=PublishedRouteController.class)
  static class Errors {
    @ExceptionHandler(NoRoute.class)
    ProblemDetail notFound() { return ProblemDetails.of(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND",
      "No complete route exists in the published model"); }
    @ExceptionHandler(UnsupportedAccessibility.class)
    ProblemDetail inaccessible() { return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "ACCESSIBILITY_UNAVAILABLE",
      "Published maps do not yet contain verified accessibility restrictions"); }
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail malformed(IllegalArgumentException e) {
      return ProblemDetails.of(HttpStatus.BAD_REQUEST, "INVALID_ROUTE_NODE", e.getMessage());
    }
  }
}
