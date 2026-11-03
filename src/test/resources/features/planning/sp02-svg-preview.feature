Feature: SP-02 Read-only SVG geometry interpretation
  As a clinic administrator
  I need a safe preview of candidate geometry
  So that I can review it before creating navigable routes

  Background:
    Given a clinic administrator registered as "svg-admin@clinica.pe" with password "Rumbo2026"
    And they sign in as "svg-admin@clinica.pe" with password "Rumbo2026"

  Scenario: Interpret a simple vector floor plan
    When I send an authenticated POST request to "/api/v1/plan-interpretations/svg" with body:
      """
      {"svg":"<svg viewBox='0 0 100 50'><line x1='0' y1='0' x2='10' y2='0'/><text x='5' y='5'>Consultorio 101</text></svg>"}
      """
    Then the response status is 200
    And the response field "/roomLabels/0/category" is "ROOM"
    And the response field "/wallCandidates/0/x2" is "10.0"
