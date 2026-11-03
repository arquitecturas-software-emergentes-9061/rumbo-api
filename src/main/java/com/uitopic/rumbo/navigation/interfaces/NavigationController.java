package com.uitopic.rumbo.navigation.interfaces;
import com.uitopic.rumbo.navigation.application.*;
import com.uitopic.rumbo.navigation.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@Profile("demo & !postgres")
@RequestMapping("/api/v1/navigations")
public final class NavigationController {
  private final GetDestinationDirectoryQueryHandler directory;
  private final CalculateRouteCommandHandler routing;
  public NavigationController(GetDestinationDirectoryQueryHandler directory,CalculateRouteCommandHandler routing) {
    this.directory=directory;this.routing=routing;
  }
  @GetMapping("/destinations")
  public ResponseEntity<List<DestinationResponse>> destinations(@RequestParam UUID siteId,
    @RequestParam(defaultValue="") String query,@RequestParam(defaultValue="false") boolean accessibleOnly) {
    return ResponseEntity.ok(directory.handle(new GetDestinationDirectoryQuery(siteId,query,accessibleOnly))
      .stream().map(DestinationResponse::from).toList());
  }
  @PostMapping("/route")
  public ResponseEntity<RouteResponse> route(@Valid @RequestBody RouteRequest request) {
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
