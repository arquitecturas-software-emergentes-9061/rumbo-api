package com.uitopic.rumbo.planning.domain.model.aggregates;

import static org.junit.jupiter.api.Assertions.*;

import com.uitopic.rumbo.planning.domain.exceptions.InvalidMapGraphException;
import com.uitopic.rumbo.planning.domain.exceptions.InvalidMapVersionStateException;
import com.uitopic.rumbo.planning.domain.exceptions.MapPublicationRejectedException;
import com.uitopic.rumbo.planning.domain.model.entities.MapConnection;
import com.uitopic.rumbo.planning.domain.model.entities.MapNode;
import com.uitopic.rumbo.planning.domain.model.valueobjects.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IndoorMapTest {
  private final UUID versionId = UUID.randomUUID();
  private final UUID floorId = UUID.randomUUID();
  private final UUID planVersionId = UUID.randomUUID();
  private final PlanCoordinateSpace space = new PlanCoordinateSpace(floorId, planVersionId, 100, 100);
  private final IndoorMap map = new IndoorMap(UUID.randomUUID(), UUID.randomUUID(), versionId);

  @BeforeEach
  void registerPlan() {
    map.addPlan(space);
    map.setScale(new MapScale(floorId, planVersionId, 0.25));
  }

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
  void acceptsDirectoryEntryOnlyAtADestinationNode() {
    MapNode access = node(NodeType.ACCESS, 1);
    MapNode destination = node(NodeType.DESTINATION, 2);
    map.addNode(access);
    map.addNode(destination);
    map.addConnection(connection(access.id(), destination.id(), true));
    UUID entryId = UUID.randomUUID();

    map.addDestination(entryId, destination.id(), DirectoryEntryType.DOCTOR,
        "Dra. Núñez", "Cardiología");

    assertEquals(1, map.destinations().size());
    assertEquals("Dra. Núñez", map.destinations().getFirst().name());
    assertTrue(map.validateForPublication().isValid());
    map.publish();
    assertThrows(InvalidMapVersionStateException.class,
        () -> map.addDestination(UUID.randomUUID(), destination.id(),
            DirectoryEntryType.SERVICE, "Farmacia", null));
  }

  @Test
  void rejectsDirectoryEntryWithoutCurrentDestinationNode() {
    MapNode access = node(NodeType.ACCESS, 1);
    map.addNode(access);
    UUID missingEntryId = UUID.randomUUID();
    InvalidMapGraphException missing = assertThrows(InvalidMapGraphException.class,
        () -> map.addDestination(missingEntryId, UUID.randomUUID(),
            DirectoryEntryType.SERVICE, "Farmacia", null));
    assertEquals(GraphIssueCode.MISSING_DESTINATION_NODE, missing.issue().code());
    assertEquals(missingEntryId, missing.issue().elementId());

    UUID wrongTypeEntryId = UUID.randomUUID();
    InvalidMapGraphException wrongType = assertThrows(InvalidMapGraphException.class,
        () -> map.addDestination(wrongTypeEntryId, access.id(),
            DirectoryEntryType.SERVICE, "Farmacia", null));
    assertEquals(GraphIssueCode.INVALID_DESTINATION_NODE_TYPE, wrongType.issue().code());
    assertTrue(map.destinations().isEmpty());
  }

  @Test
  void rejectsDuplicateOrForeignVersionDirectoryEntries() {
    MapNode destination = node(NodeType.DESTINATION, 2);
    map.addNode(destination);
    UUID entryId = UUID.randomUUID();
    map.addDestination(entryId, destination.id(), DirectoryEntryType.SERVICE,
        "Farmacia", null);
    InvalidMapGraphException duplicate = assertThrows(InvalidMapGraphException.class,
        () -> map.addDestination(entryId, destination.id(), DirectoryEntryType.SERVICE,
            "Otra farmacia", null));
    assertEquals(GraphIssueCode.DUPLICATE_DESTINATION_ID, duplicate.issue().code());

    var foreign = new com.uitopic.rumbo.planning.domain.model.entities.MapDestination(
        UUID.randomUUID(), UUID.randomUUID(), destination.id(),
        DirectoryEntryType.SERVICE, "Laboratorio", null);
    InvalidMapGraphException wrongVersion = assertThrows(InvalidMapGraphException.class,
        () -> map.addDestination(foreign));
    assertEquals(GraphIssueCode.WRONG_MODEL_VERSION, wrongVersion.issue().code());
    assertEquals(1, map.destinations().size());
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
  void editorNodeOutsidePlanReportsItsIdentifier() {
    UUID nodeId = UUID.randomUUID();
    InvalidMapGraphException error = assertThrows(InvalidMapGraphException.class,
        () -> map.addNode(nodeId, floorId, planVersionId, 101, 1, NodeType.ACCESS));
    assertEquals(GraphIssueCode.INVALID_NODE_DATA, error.issue().code());
    assertEquals(nodeId, error.issue().elementId());
    assertTrue(map.nodes().isEmpty());
  }

  @Test
  void editorConnectionWithMissingEndpointReportsItsIdentifier() {
    UUID accessId = UUID.randomUUID();
    map.addNode(accessId, floorId, planVersionId, 1, 1, NodeType.ACCESS);
    UUID connectionId = UUID.randomUUID();
    InvalidMapGraphException error = assertThrows(InvalidMapGraphException.class,
        () -> map.addConnection(connectionId, accessId, UUID.randomUUID(),
            ConnectionType.CORRIDOR, 2, 3, true));
    assertEquals(GraphIssueCode.MISSING_TARGET_NODE, error.issue().code());
    assertEquals(connectionId, error.issue().elementId());
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

  @Test
  void retainsPlanAndConfirmedScaleWithinDraft() {
    MapScale scale = new MapScale(floorId, planVersionId, 0.25);
    map.setScale(scale);

    assertEquals(java.util.List.of(space), map.plans());
    assertEquals(java.util.List.of(scale), map.scales());
    assertThrows(UnsupportedOperationException.class, () -> map.plans().clear());
  }

  @Test
  void rejectsNodesFromUnregisteredPlanVersion() {
    UUID otherPlan = UUID.randomUUID();
    PlanCoordinateSpace otherSpace = new PlanCoordinateSpace(floorId, otherPlan, 100, 100);
    MapNode node = new MapNode(UUID.randomUUID(), versionId,
        new SpatialCoordinate(floorId, otherPlan, 1, 1), NodeType.ACCESS, otherSpace);

    InvalidMapGraphException error = assertThrows(InvalidMapGraphException.class,
        () -> map.addNode(node));
    assertEquals(GraphIssueCode.MISSING_PLAN_VERSION, error.issue().code());
    assertTrue(map.nodes().isEmpty());
  }

  @Test
  void publishesValidGraphAndPreventsFurtherEdits() {
    MapNode access = node(NodeType.ACCESS, 1);
    MapNode destination = node(NodeType.DESTINATION, 2);
    map.addNode(access);
    map.addNode(destination);
    map.addConnection(connection(access.id(), destination.id(), true));

    map.publish();

    assertEquals(MapVersionStatus.PUBLISHED, map.status());
    assertThrows(InvalidMapVersionStateException.class, map::publish);
    assertThrows(InvalidMapVersionStateException.class, () -> map.addNode(node(NodeType.INTERSECTION, 3)));
  }

  @Test
  void failedPublicationLeavesDraftEditableAndReportsIssues() {
    MapNode access = node(NodeType.ACCESS, 1);
    MapNode destination = node(NodeType.DESTINATION, 2);
    map.addNode(access);
    map.addNode(destination);

    MapPublicationRejectedException error = assertThrows(MapPublicationRejectedException.class, map::publish);

    assertEquals(MapVersionStatus.DRAFT, map.status());
    assertTrue(error.validation().issues().stream().anyMatch(issue ->
        issue.code() == GraphIssueCode.DISCONNECTED_NODE && destination.id().equals(issue.elementId())));
    map.addConnection(connection(access.id(), destination.id(), true));
    map.publish();
    assertEquals(MapVersionStatus.PUBLISHED, map.status());
  }

  @Test
  void requiresConfirmedScaleBeforePublication() {
    IndoorMap unscaled = new IndoorMap(UUID.randomUUID(), UUID.randomUUID(), versionId);
    unscaled.addPlan(space);
    MapNode access = node(NodeType.ACCESS, 1);
    MapNode destination = node(NodeType.DESTINATION, 2);
    unscaled.addNode(access);
    unscaled.addNode(destination);
    unscaled.addConnection(connection(access.id(), destination.id(), true));

    MapPublicationRejectedException error = assertThrows(MapPublicationRejectedException.class, unscaled::publish);

    assertTrue(error.validation().issues().stream().anyMatch(issue ->
        issue.code() == GraphIssueCode.MISSING_SCALE
            && issue.elementType() == GraphElementType.PLAN
            && planVersionId.equals(issue.elementId())));
  }

  @Test
  void rejectsCrossFloorCorridorAndAcceptsVerticalConnector() {
    UUID otherFloorId = UUID.randomUUID();
    UUID otherPlanVersionId = UUID.randomUUID();
    PlanCoordinateSpace otherSpace = new PlanCoordinateSpace(otherFloorId, otherPlanVersionId, 100, 100);
    map.addPlan(otherSpace);
    map.setScale(new MapScale(otherFloorId, otherPlanVersionId, 0.25));
    MapNode access = node(NodeType.ACCESS, 1);
    MapNode destination = new MapNode(UUID.randomUUID(), versionId,
        new SpatialCoordinate(otherFloorId, otherPlanVersionId, 2, 1), NodeType.DESTINATION, otherSpace);
    map.addNode(access);
    map.addNode(destination);
    MapConnection corridor = connection(access.id(), destination.id(), true);
    InvalidMapGraphException error = assertThrows(InvalidMapGraphException.class,
        () -> map.addConnection(corridor));
    assertEquals(GraphIssueCode.INVALID_VERTICAL_CONNECTION, error.issue().code());
    assertEquals(corridor.id(), error.issue().elementId());
    assertTrue(map.connections().isEmpty());
    assertEquals(MapVersionStatus.DRAFT, map.status());

    map.addConnection(new MapConnection(UUID.randomUUID(), versionId, access.id(),
        destination.id(), ConnectionType.ELEVATOR, 4, 6, 3, true));
    assertEquals(3, map.connections().getFirst().verticalPenalty());
    map.publish();
    assertEquals(MapVersionStatus.PUBLISHED, map.status());
  }

  @Test
  void rejectsVerticalPenaltyOnSameFloorWithoutChangingDraft() {
    MapNode access = node(NodeType.ACCESS, 1);
    MapNode destination = node(NodeType.DESTINATION, 2);
    map.addNode(access);
    map.addNode(destination);
    UUID connectionId = UUID.randomUUID();

    InvalidMapGraphException error = assertThrows(InvalidMapGraphException.class,
        () -> map.addConnection(connectionId, access.id(), destination.id(),
            ConnectionType.STAIRS, 2, 2, 3, true));

    assertEquals(GraphIssueCode.INVALID_CONNECTION_DATA, error.issue().code());
    assertEquals(connectionId, error.issue().elementId());
    assertTrue(map.connections().isEmpty());
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
