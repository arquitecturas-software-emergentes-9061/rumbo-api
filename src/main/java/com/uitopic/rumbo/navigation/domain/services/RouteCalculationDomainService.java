package com.uitopic.rumbo.navigation.domain.services;
import com.uitopic.rumbo.navigation.domain.exceptions.NavigationValidationException;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.NodeKey;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedRouteNetwork;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.RouteEdge;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.RouteNode;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.RoutePath;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.WallSegment;

import java.util.*;
/** Dijkstra ported from the FastAPI MVP's routing policy (no dependency on Spring). */
public final class RouteCalculationDomainService {
  private record Step(NodeKey from, RouteEdge via) {}
  private record Candidate(NodeKey node, double distance) {}

  public RoutePath calculate(PublishedRouteNetwork network, NodeKey start, NodeKey destination,
                             boolean accessibleOnly) {
    Map<NodeKey, RouteNode> nodes = new HashMap<>();
    for (RouteNode node : network.nodes()) nodes.put(node.key(), node);
    if (!nodes.containsKey(start) || !nodes.containsKey(destination))
      throw new NavigationValidationException("El origen o el destino no existe en el modelo publicado.");

    Map<NodeKey,List<RouteEdge>> adjacency = new HashMap<>();
    for (NodeKey key : nodes.keySet()) adjacency.put(key, new ArrayList<>());
    for (RouteEdge edge : network.edges()) {
      if (accessibleOnly && !edge.accessible()) continue;
      RouteNode a = nodes.get(edge.from()), b = nodes.get(edge.to());
      if (a == null || b == null) continue;
      if (!a.floorId().equals(b.floorId()) && !Set.of("elevator", "stairs", "ramp").contains(edge.kind())) continue;
      if (crossesWall(a,b,network.walls())) continue;
      adjacency.get(edge.from()).add(edge);
      if (edge.bidirectional()) adjacency.get(edge.to()).add(edge.reversed());
    }

    Map<NodeKey,Double> dist = new HashMap<>();
    Map<NodeKey,Step> previous = new HashMap<>();
    PriorityQueue<Candidate> queue = new PriorityQueue<>(Comparator.comparingDouble(Candidate::distance));
    dist.put(start,0.0);queue.add(new Candidate(start,0.0));
    while(!queue.isEmpty()) {
      Candidate curr = queue.poll();
      if(curr.distance() > dist.getOrDefault(curr.node(),Double.POSITIVE_INFINITY)) continue;
      if(curr.node().equals(destination)) break;
      for(RouteEdge edge: adjacency.get(curr.node())) {
        double next=curr.distance()+edge.distanceMeters();
        if(next < dist.getOrDefault(edge.to(),Double.POSITIVE_INFINITY)) {
          dist.put(edge.to(), next); previous.put(edge.to(), new Step(curr.node(),edge));
          queue.add(new Candidate(edge.to(),next));
        }
      }
    }
    if(!dist.containsKey(destination)) {
      throw new NavigationValidationException(accessibleOnly
        ? "No existe una ruta accesible entre origen y destino."
        : "No existe una ruta entre origen y destino.");
    }
    List<RouteNode> orderedNodes=new ArrayList<>();List<RouteEdge> orderedEdges=new ArrayList<>();
    NodeKey current=destination;
    orderedNodes.add(nodes.get(current));
    while(!current.equals(start)) {
      Step step=previous.get(current);
      if(step==null) throw new NavigationValidationException("Ruta incompleta en el modelo publicado.");
      orderedEdges.add(step.via());current=step.from();orderedNodes.add(nodes.get(current));
    }
    Collections.reverse(orderedNodes);Collections.reverse(orderedEdges);
    return new RoutePath(orderedNodes,orderedEdges,dist.get(destination));
  }
  private static boolean crossesWall(RouteNode a,RouteNode b,List<WallSegment> walls) {
    if(!a.floorId().equals(b.floorId())) return false; // vertical connector is not a wall segment
    return walls.stream().filter(w->w.floorId().equals(a.floorId()))
      .anyMatch(w->w.properlyCrosses(a.x(),a.y(),b.x(),b.y()));
  }
}
