package com.uitopic.rumbo.planning.domain.model.aggregates;

import static org.junit.jupiter.api.Assertions.*;

import com.uitopic.rumbo.planning.domain.exceptions.InvalidMapGraphException;
import com.uitopic.rumbo.planning.domain.model.entities.MapConnection;
import com.uitopic.rumbo.planning.domain.model.entities.MapNode;
import com.uitopic.rumbo.planning.domain.model.valueobjects.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IndoorMapTest {
  private final UUID versionId = UUID.randomUUID();
  private final UUID floorId = UUID.randomUUID();
  private final UUID planVersionId = UUID.randomUUID();
  private final PlanCoordinateSpace space = new PlanCoordinateSpace(floorId, planVersionId, 100, 100);
  private final IndoorMap map = new IndoorMap(UUID.randomUUID(), UUID.randomUUID(), versionId);

  @Test
  void acceptsConnectedDraftWithReachableDestination() {
    MapNode access = node(NodeType.ACCESS, 1);
    MapNode destination = node(NodeType.DESTINATION, 2);
    map.addNode(access);
    map.addNode(destination);
    map.addConnection(connection(access.id(), destination.id(), true));

    assertTrue(map.validateForPublication().isValid());
    assertEquals(2, map.nodes().size());
    assertEquals(1, map.connections().size());
  }

  @Test
  void rejectsMissingEndpointWithoutChangingDraft() {
    MapNode access = node(NodeType.ACCESS, 1);
    map.addNode(access);
    MapConnection connection = connection(access.id(), UUID.randomUUID(), true);

    InvalidMapGraphException error = assertThrows(InvalidMapGraphException.class,
        () -> map.addConnection(connection));
    assertEquals(GraphIssueCode.MISSING_TARGET_NODE, error.issue().code());
    assertEquals(GraphElementType.CONNECTION, error.issue().elementType());
    assertEquals(connection.id(), error.issue().elementId());
    assertTrue(map.connections().isEmpty());
  }

  @Test
  void rejectsRepeatedNodeAndConnectionTraversals() {
    MapNode access = node(NodeType.ACCESS, 1);
    MapNode destination = node(NodeType.DESTINATION, 2);
    map.addNode(access);
    map.addNode(destination);
    MapNode repeated = new MapNode(access.id(), versionId,
        new SpatialCoordinate(floorId, planVersionId, 3, 3), NodeType.ACCESS, space);
    InvalidMapGraphException nodeError = assertThrows(InvalidMapGraphException.class,
        () -> map.addNode(repeated));
    assertEquals(GraphIssueCode.DUPLICATE_NODE_ID, nodeError.issue().code());

    map.addConnection(connection(access.id(), destination.id(), true));
    MapConnection reverse = connection(destination.id(), access.id(), false);
    InvalidMapGraphException edgeError = assertThrows(InvalidMapGraphException.class,
        () -> map.addConnection(reverse));
    assertEquals(GraphIssueCode.DUPLICATE_CONNECTION, edgeError.issue().code());
    assertEquals(reverse.id(), edgeError.issue().elementId());
    assertEquals(1, map.connections().size());
  }

  @Test
  void keepsOppositeOneWayLinksDistinct() {
    MapNode access = node(NodeType.ACCESS, 1);
    MapNode destination = node(NodeType.DESTINATION, 2);
    map.addNode(access);
    map.addNode(destination);
    map.addConnection(connection(access.id(), destination.id(), false));
    map.addConnection(connection(destination.id(), access.id(), false));
    assertEquals(2, map.connections().size());
    assertTrue(map.validateForPublication().isValid());
  }

  @Test
  void reportsDisconnectedDestinationAtItsNode() {
    MapNode access = node(NodeType.ACCESS, 1);
    MapNode destination = node(NodeType.DESTINATION, 2);
    map.addNode(access);
    map.addNode(destination);

    var result = map.validateForPublication();

    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == GraphIssueCode.DISCONNECTED_NODE
            && issue.elementType() == GraphElementType.NODE
            && destination.id().equals(issue.elementId())));
  }

  @Test
  void reportsOneWayDestinationThatCannotBeReachedFromAccess() {
    MapNode access = node(NodeType.ACCESS, 1);
    MapNode destination = node(NodeType.DESTINATION, 2);
    map.addNode(access);
    map.addNode(destination);
    map.addConnection(connection(destination.id(), access.id(), false));

    var result = map.validateForPublication();

    assertEquals(1, result.issues().size());
    assertEquals(GraphIssueCode.UNREACHABLE_DESTINATION, result.issues().getFirst().code());
    assertEquals(destination.id(), result.issues().getFirst().elementId());
  }

  @Test
  void reportsSeparateComponentsEvenWhenBothHaveAnAccess() {
    MapNode firstAccess = node(NodeType.ACCESS, 1);
    MapNode firstDestination = node(NodeType.DESTINATION, 2);
    MapNode secondAccess = node(NodeType.ACCESS, 3);
    MapNode secondDestination = node(NodeType.DESTINATION, 4);
    map.addNode(firstAccess);
    map.addNode(firstDestination);
    map.addNode(secondAccess);
    map.addNode(secondDestination);
    map.addConnection(connection(firstAccess.id(), firstDestination.id(), true));
    map.addConnection(connection(secondAccess.id(), secondDestination.id(), true));

    var result = map.validateForPublication();

    assertEquals(2, result.issues().stream()
        .filter(issue -> issue.code() == GraphIssueCode.DISCONNECTED_NODE).count());
  }

  @Test
  void emptyDraftReportsGraphWideIssuesInImmutableResult() {
    var result = map.validateForPublication();

    assertFalse(result.isValid());
    assertTrue(result.issues().stream().anyMatch(issue ->
        issue.code() == GraphIssueCode.EMPTY_GRAPH
            && issue.elementType() == GraphElementType.GRAPH && issue.elementId() == null));
    assertThrows(UnsupportedOperationException.class, () -> result.issues().clear());
  }

  @Test
  void rejectsForeignModelVersion() {
    MapNode foreign = new MapNode(UUID.randomUUID(), UUID.randomUUID(),
        new SpatialCoordinate(floorId, planVersionId, 1, 1), NodeType.ACCESS, space);
    InvalidMapGraphException error = assertThrows(InvalidMapGraphException.class,
        () -> map.addNode(foreign));
    assertEquals(GraphIssueCode.WRONG_MODEL_VERSION, error.issue().code());
    assertEquals(foreign.id(), error.issue().elementId());
  }

  private MapNode node(NodeType type, double x) {
    return new MapNode(UUID.randomUUID(), versionId,
        new SpatialCoordinate(floorId, planVersionId, x, 1), type, space);
  }

  private MapConnection connection(UUID from, UUID to, boolean bidirectional) {
    return new MapConnection(UUID.randomUUID(), versionId, from, to,
        ConnectionType.CORRIDOR, 2, 2, bidirectional);
  }
}
