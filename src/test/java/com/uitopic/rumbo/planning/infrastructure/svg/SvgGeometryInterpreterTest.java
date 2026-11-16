package com.uitopic.rumbo.planning.infrastructure.svg;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SvgGeometryInterpreterTest {
  private final SvgGeometryInterpreter parser = new SvgGeometryInterpreter();

  @Test void extractsWallsAndRoomNamesWithoutInventingRoutes() {
    String svg = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 50">
        <line x1="0" y1="0" x2="10" y2="0"/>
        <rect x="20" y="20" width="10" height="5"/>
        <text x="25" y="22">Consultorio 101</text>
        <text x="3" y="10">Pasillo principal</text>
      </svg>
      """;
    var result = parser.interpret(svg);
    assertEquals(5, result.wallCandidates().size());
    assertEquals(2, result.roomLabels().size());
    assertEquals("ROOM", result.roomLabels().get(0).category());
    assertEquals("CORRIDOR", result.roomLabels().get(1).category());
    assertEquals(100, result.viewBox().width());
    assertTrue(result.warnings().stream().anyMatch(w -> w.contains("no paredes confirmadas")));
  }

  @Test void parsesPathLineCommandsAndRelativeCoordinates() {
    var result = parser.interpret("""
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
        <path d="M 1 1 h 2 v 3 l -2 0 z"/>
      </svg>
      """);
    assertEquals(4, result.wallCandidates().size());
    assertEquals(3, result.wallCandidates().get(0).x2());
    assertEquals(4, result.wallCandidates().get(1).y2());
  }

  @Test void refusesXmlDoctypeAndUnsupportedTransform() {
    assertThrows(SvgGeometryInterpreter.InvalidSvg.class,
      () -> parser.interpret("<!DOCTYPE svg [<!ENTITY data SYSTEM 'file:///etc/passwd'>]><svg>&data;</svg>"));
    assertThrows(SvgGeometryInterpreter.InvalidSvg.class,
      () -> parser.interpret("<svg viewBox='0 0 10 10'><g transform='scale(2)'><line x2='3'/></g></svg>"));
  }

  @Test void dropsCurvesInsteadOfGuessingWalls() {
    var result = parser.interpret("<svg viewBox='0 0 10 10'><path d='M 0 0 C 2 3 4 5 6 7'/></svg>");
    assertTrue(result.wallCandidates().isEmpty());
    assertTrue(result.warnings().stream().anyMatch(w -> w.contains("curvas")));
  }

  @Test void rejectsInvalidGeometryAndHugeInputs() {
    assertThrows(SvgGeometryInterpreter.InvalidSvg.class,
      () -> parser.interpret("<svg><rect x='0' y='0' width='-2' height='3'/></svg>"));
    assertThrows(SvgGeometryInterpreter.InvalidSvg.class,
      () -> parser.interpret(" ".repeat(1_000_001)));
  }
}
