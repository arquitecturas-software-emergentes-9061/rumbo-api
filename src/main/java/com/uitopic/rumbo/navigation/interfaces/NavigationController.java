package com.uitopic.rumbo.navigation.interfaces;
import com.uitopic.rumbo.navigation.application.*;
import com.uitopic.rumbo.navigation.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@Tag(name = "Navigation (provisional)", description = "Demo route and destination endpoints. Published Planning routes are not yet exposed.")
@RequestMapping("/api/v1/navigations")
public final class NavigationController {
  private final GetDestinationDirectoryQueryHandler directory;
  private final CalculateRouteCommandHandler routing;
  public NavigationController(GetDestinationDirectoryQueryHandler directory,CalculateRouteCommandHandler routing) {
    this.directory=directory;this.routing=routing;
  }
  @Operation(summary = "Search published demo destinations", description = "Requires a UUID siteId; query matches name, specialty and category without accents. Demo fixture only until Planning integration.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Matching destinations"),
    @ApiResponse(responseCode = "400", description = "Invalid or missing siteId")
  })
  @GetMapping("/destinations")
  public ResponseEntity<List<DestinationResponse>> destinations(@RequestParam UUID siteId,
    @RequestParam(defaultValue="") String query,@RequestParam(defaultValue="false") boolean accessibleOnly) {
    return ResponseEntity.ok(directory.handle(new GetDestinationDirectoryQuery(siteId,query,accessibleOnly))
      .stream().map(DestinationResponse::from).toList());
  }
  @Operation(summary = "Calculate a route over the provisional navigation graph",
    description = "Uses Dijkstra with distanceMeters as its current cost. Explicit stairs, ramp or elevator links are required for floor changes; accessibleOnly filters non-accessible links. This POST contract is provisional and does NOT promise persisted A* weights or real Planning maps.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Ordered route nodes, links and total physical distance",
      content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Valid demo route",
        value = "{\"totalDistanceMeters\":8.0,\"nodes\":[{\"floorId\":\"00000000-0000-4000-8000-000000000002\",\"nodeId\":\"00000000-0000-4000-8000-000000000010\",\"label\":\"Entrada\",\"x\":0.0,\"y\":0.0},{\"floorId\":\"00000000-0000-4000-8000-000000000002\",\"nodeId\":\"00000000-0000-4000-8000-000000000011\",\"label\":\"Consultorio 101\",\"x\":8.0,\"y\":0.0}],\"links\":[{\"from\":{\"floorId\":\"00000000-0000-4000-8000-000000000002\",\"nodeId\":\"00000000-0000-4000-8000-000000000010\"},\"to\":{\"floorId\":\"00000000-0000-4000-8000-000000000002\",\"nodeId\":\"00000000-0000-4000-8000-000000000011\"},\"distanceMeters\":8.0,\"kind\":\"corridor\",\"accessible\":true}]}"))),
    @ApiResponse(responseCode = "400", description = "Malformed or missing request fields"),
    @ApiResponse(responseCode = "422", description = "Unknown site, unknown nodes, inaccessible or disconnected route")
  })
  @PostMapping("/route")
  public ResponseEntity<RouteResponse> route(
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
      required = true, description = "Site and origin/destination node keys, plus optional accessibility filter.",
      content = @Content(mediaType = "application/json",
        examples = @ExampleObject(name = "Cardiology demo", value =
          "{\"siteId\":\"00000000-0000-4000-8000-000000000001\",\"startFloorId\":\"00000000-0000-4000-8000-000000000002\",\"startNodeId\":\"00000000-0000-4000-8000-000000000010\",\"destinationFloorId\":\"00000000-0000-4000-8000-000000000002\",\"destinationNodeId\":\"00000000-0000-4000-8000-000000000011\",\"accessibleOnly\":true}")))
    @Valid @RequestBody RouteRequest request) {
    RoutePath path=routing.handle(new CalculateRouteCommand(request.siteId(),request.startFloorId(),request.startNodeId(),
      request.destinationFloorId(),request.destinationNodeId(),request.accessibleOnly()));
    return ResponseEntity.ok(new RouteResponse(path.totalDistanceMeters(),
      path.nodes().stream().map(n->new RouteNodeResponse(n.floorId(),n.id(),n.label(),n.x(),n.y())).toList(),
      path.edges().stream().map(e->new RouteEdgeResponse(e.from(),e.to(),e.distanceMeters(),e.kind(),e.accessible())).toList()));
  }
  public record DestinationResponse(UUID id,UUID siteId,UUID floorId,UUID nodeId,String name,String specialty,String category,boolean accessible) {
    public static DestinationResponse from(Destination d) {return new DestinationResponse(d.id(),d.siteId(),d.floorId(),d.nodeId(),d.name(),d.specialty(),d.category(),d.accessible());}
  }
  public record RouteRequest(@NotNull UUID siteId,@NotNull UUID startFloorId,@NotNull UUID startNodeId,
    @NotNull UUID destinationFloorId,@NotNull UUID destinationNodeId,boolean accessibleOnly) {}
  public record RouteNodeResponse(UUID floorId,UUID nodeId,String label,double x,double y) {}
  public record RouteEdgeResponse(NodeKey from,NodeKey to,double distanceMeters,String kind,boolean accessible) {}
  public record RouteResponse(double totalDistanceMeters,List<RouteNodeResponse> nodes,List<RouteEdgeResponse> links) {}
}
