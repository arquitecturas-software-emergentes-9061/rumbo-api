package com.uitopic.rumbo.planning.infrastructure.svg;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class SvgRoomTopologyProposalServiceTest {
  private final SvgRoomTopologyProposalService service = new SvgRoomTopologyProposalService();

  @Test void findsTwoLabeledZonesAndConnectsThemOnlyThroughAnExplicitDoor() {
    String svg = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 10">
        <rect data-rumbo-role="room" x="0" y="0" width="10" height="10"/>
        <rect data-rumbo-role="corridor" x="10" y="0" width="10" height="10"/>
        <line data-rumbo-role="door" x1="10" y1="4" x2="10" y2="6"/>
        <text x="5" y="5">Consultorio 101</text>
        <text x="15" y="5">Pasillo principal</text>
      </svg>
      """;
    var proposal = service.propose(svg);
    assertEquals(2, proposal.zones().size());
    assertEquals(1, proposal.doors().size());
    assertEquals(3, proposal.nodes().size());
    assertEquals(2, proposal.connections().size());
    assertEquals(List.of("ROOM", "CORRIDOR"),
      proposal.zones().stream().map(SvgRoomTopologyProposalService.Zone::category).toList());
    assertEquals(5.0, proposal.connections().get(0).distanceUnits(), 1e-9);
    assertTrue(proposal.wallCandidates().isEmpty(), "A door must not be treated as a blocking wall");
  }

  @Test void refusesToLinkRoomsWithoutAVisibleOpening() {
    var proposal = service.propose("""
      <svg viewBox="0 0 20 10">
        <rect data-rumbo-role="room" x="0" y="0" width="10" height="10"/>
        <rect data-rumbo-role="corridor" x="10" y="0" width="10" height="10"/>
      </svg>
      """);
    assertEquals(2, proposal.zones().size());
    assertTrue(proposal.connections().isEmpty());
    assertTrue(proposal.warnings().stream().anyMatch(s -> s.contains("No hay conexiones")));
  }

  @Test void keepsWallsAndDoorsButOmitsRoutesThatCrossAnUnopenedWall() {
    var proposal = service.propose("""
      <svg viewBox="0 0 20 10">
        <rect data-rumbo-role="room" x="0" y="0" width="10" height="10"/>
        <rect data-rumbo-role="corridor" x="10" y="0" width="10" height="10"/>
        <line x1="7" y1="0" x2="7" y2="10"/>
        <line x1="10" y1="0" x2="10" y2="10"/>
        <line data-rumbo-role="door" x1="10" y1="4" x2="10" y2="6"/>
      </svg>
      """);
    assertEquals(2, proposal.wallCandidates().size());
    assertEquals(1, proposal.doors().size());
    assertEquals(1, proposal.connections().size());
    assertTrue(proposal.warnings().stream().anyMatch(text -> text.contains("fuera de una puerta")));
  }

  @Test void keepsAnUnmatchedDoorForReviewWithoutInventingRoutes() {
    var proposal = service.propose("""
      <svg viewBox="0 0 20 10">
        <rect data-rumbo-role="room" x="0" y="0" width="10" height="10"/>
        <rect data-rumbo-role="corridor" x="10" y="0" width="10" height="10"/>
        <line x1="3" y1="0" x2="3" y2="10"/>
        <line data-rumbo-role="door" x1="10" y1="4" x2="10" y2="6"/>
      </svg>
      """);
    assertEquals(1, proposal.wallCandidates().size());
    assertEquals(1, proposal.doors().size());
    assertTrue(proposal.connections().isEmpty());
    assertTrue(proposal.warnings().stream().anyMatch(text -> text.contains("sin un muro compatible")),
      proposal.warnings().toString());
  }

  @Test void rejectsAmbiguousDoorTouchingThreeZones() {
    var proposal = service.propose("""
      <svg viewBox="0 0 20 10">
        <rect data-rumbo-role="room" x="0" y="0" width="10" height="10"/>
        <rect data-rumbo-role="corridor" x="10" y="0" width="10" height="10"/>
        <rect data-rumbo-role="service" x="10" y="0" width="10" height="10"/>
        <line data-rumbo-role="door" x1="10" y1="4" x2="10" y2="6"/>
      </svg>
      """);
    assertTrue(proposal.connections().isEmpty());
    assertTrue(proposal.warnings().stream().anyMatch(s -> s.contains("conecta 3 zonas")));
  }

  @Test void rejectsInvalidDoorOrUnsafelyTransformedPlan() {
    assertThrows(SvgGeometryInterpreter.InvalidSvg.class,
      () -> service.propose("<svg><line data-rumbo-role='door' x1='1' y1='1' x2='1' y2='1'/></svg>"));
    assertThrows(SvgGeometryInterpreter.InvalidSvg.class,
      () -> service.propose("<svg><g transform='scale(2)'><rect data-rumbo-role='room' width='2' height='2'/></g></svg>"));
  }

  @Test void classifiesServicesAndRetainsOriginalCoordinates() {
    var proposal = service.propose("""
      <svg viewBox="0 0 40 10">
        <rect data-rumbo-role="service" x="20" y="0" width="10" height="10"/>
        <text x="25" y="5">Farmacia</text>
      </svg>
      """);
    assertEquals("SERVICE", proposal.zones().getFirst().category());
    assertEquals(25, proposal.nodes().getFirst().x());
    assertEquals(5, proposal.nodes().getFirst().y());
    assertEquals("Farmacia", proposal.zones().getFirst().name());
  }
}
