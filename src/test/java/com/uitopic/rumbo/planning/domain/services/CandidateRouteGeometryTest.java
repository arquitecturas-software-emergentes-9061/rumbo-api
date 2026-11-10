package com.uitopic.rumbo.planning.domain.services;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CandidateRouteGeometryTest {
  private static CandidateRouteGeometry.Point p(double x, double y) {
    return new CandidateRouteGeometry.Point(x, y);
  }

  private static CandidateRouteGeometry.Segment line(double x1, double y1, double x2, double y2) {
    return new CandidateRouteGeometry.Segment(p(x1, y1), p(x2, y2));
  }

  private static CandidateRouteGeometry.Polygon square(double x1, double y1, double x2, double y2) {
    return new CandidateRouteGeometry.Polygon(List.of(p(x1, y1), p(x2, y1), p(x2, y2), p(x1, y2)), List.of());
  }

  @Test void wallsBlockRoutesUntilAnAssociatedOpeningIsRecognized() {
    var wall = line(50, 0, 50, 100);
    var opening = line(50, 45, 50, 55);
    var walkable = List.of(square(0, 0, 100, 100));
    var blocked = new CandidateRouteGeometry(List.of(wall), List.of(), List.of(), walkable, false);
    assertFalse(blocked.review(p(20, 50), p(80, 50), "CORRIDOR").accepted());
    assertTrue(CandidateRouteGeometry.openingFitsWall(opening, List.of(wall)));
    var valid = new CandidateRouteGeometry(List.of(wall), List.of(opening), List.of(), walkable, false);
    assertEquals("DOORWAY", valid.review(p(20, 50), p(80, 50), "CORRIDOR").kind());
    assertFalse(valid.review(p(20, 20), p(80, 20), "DOORWAY").accepted());
    assertFalse(CandidateRouteGeometry.openingFitsWall(line(60, 45, 60, 55), List.of(wall)));
  }

  @Test void barriersAndWalkableGapsCannotBeCrossedEvenWithAnOpening() {
    var opening = line(50, 45, 50, 55);
    var obstacle = new CandidateRouteGeometry(List.of(), List.of(opening), List.of(square(45, 40, 55, 60)),
      List.of(square(0, 0, 100, 100)), false);
    assertFalse(obstacle.review(p(20, 50), p(80, 50), "DOORWAY").accepted());
    var separated = new CandidateRouteGeometry(List.of(), List.of(), List.of(),
      List.of(square(0, 0, 40, 100), square(60, 0, 100, 100)), false);
    assertFalse(separated.review(p(20, 50), p(80, 50), "CORRIDOR").accepted());
    var unknown = new CandidateRouteGeometry(List.of(), List.of(), List.of(), List.of(), false);
    assertTrue(unknown.review(p(20, 50), p(80, 50), "CORRIDOR").warning().contains("espacio transitable"));
  }

  @Test void routeAlongAWallIsNotMistakenForOpenFloor() {
    var geometry = new CandidateRouteGeometry(List.of(line(20, 50, 80, 50)), List.of(), List.of(),
      List.of(square(0, 0, 100, 100)), false);
    assertFalse(geometry.review(p(30, 50), p(70, 50), "CORRIDOR").accepted());
  }

  @Test void aRecognizedGapBetweenWallEndsCanHostAnOpening() {
    var walls = List.of(line(50, 0, 50, 45), line(50, 55, 50, 100));
    var opening = line(50, 45, 50, 55);
    assertFalse(CandidateRouteGeometry.openingFitsWall(opening, walls));
    assertTrue(CandidateRouteGeometry.openingFitsWallGap(opening, walls));
    assertFalse(CandidateRouteGeometry.openingFitsWallGap(line(60, 45, 60, 55), walls));
    assertFalse(CandidateRouteGeometry.openingFitsWallGap(line(40, 50, 60, 50),
      List.of(line(40, 0, 40, 100), line(60, 0, 60, 100))));
    assertFalse(CandidateRouteGeometry.openingFitsWallGap(line(50, 45, 50, 55),
      List.of(line(50, 0, 50, 100))));
  }
}
