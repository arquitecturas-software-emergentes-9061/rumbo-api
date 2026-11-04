package com.uitopic.rumbo.navigation.interfaces.rest;

import com.uitopic.rumbo.navigation.domain.model.queries.CalculateRouteQuery;
import com.uitopic.rumbo.navigation.domain.model.queries.GetDestinationDirectoryQuery;
import com.uitopic.rumbo.navigation.domain.services.DestinationQueryService;
import com.uitopic.rumbo.navigation.domain.services.RouteQueryService;
import com.uitopic.rumbo.navigation.interfaces.rest.resources.DestinationResponse;
import com.uitopic.rumbo.navigation.interfaces.rest.resources.RouteRequest;
import com.uitopic.rumbo.navigation.interfaces.rest.resources.RouteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Provisional routes and destination search backed only by the demo fixture. */
@RestController
@Profile({"demo & !postgres", "postgres & local"})
@Tag(name = "Demo navigation", description = "Provisional endpoints using fixed demonstration data")
@RequestMapping("/api/v1/navigations")
public final class NavigationController {
  private final DestinationQueryService directory;
  private final RouteQueryService routing;

  public NavigationController(DestinationQueryService directory, RouteQueryService routing) {
    this.directory = directory;
    this.routing = routing;
  }

  /** Searches demo destinations within a site. */
  @Operation(summary = "Search demonstration destinations")
  @GetMapping("/destinations")
  public ResponseEntity<List<DestinationResponse>> destinations(@RequestParam UUID siteId,
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "false") boolean accessibleOnly) {
    return ResponseEntity.ok(directory.search(
        new GetDestinationDirectoryQuery(siteId, query, accessibleOnly))
        .stream().map(DestinationResponse::from).toList());
  }

  /** Calculates a provisional route using the demo routing policy. */
  @Operation(summary = "Calculate a demonstration route")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Demonstration route found",
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = RouteResponse.class),
              examples = @ExampleObject(name = "demoRoute", value = """
                  {"totalDistanceMeters":5.0,"nodes":[],"links":[]}
                  """))),
      @ApiResponse(responseCode = "400", description = "Invalid request"),
      @ApiResponse(responseCode = "422", description = "No navigable demonstration route")
  })
  @PostMapping("/route")
  public ResponseEntity<RouteResponse> route(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          content = @Content(mediaType = "application/json",
              schema = @Schema(implementation = RouteRequest.class),
              examples = @ExampleObject(name = "demoRouteRequest", value = """
                  {"siteId":"00000000-0000-4000-8000-000000000001",
                   "startFloorId":"00000000-0000-4000-8000-000000000002",
                   "startNodeId":"00000000-0000-4000-8000-000000000003",
                   "destinationFloorId":"00000000-0000-4000-8000-000000000002",
                   "destinationNodeId":"00000000-0000-4000-8000-000000000004",
                   "accessibleOnly":false}
                  """)))
      @Valid @RequestBody RouteRequest request) {
    return ResponseEntity.ok(RouteResponse.from(routing.calculate(new CalculateRouteQuery(
        request.siteId(), request.startFloorId(), request.startNodeId(),
        request.destinationFloorId(), request.destinationNodeId(), request.accessibleOnly()))));
  }
}
