Feature: US-14 Registered BLE beacon inventory
  As a clinic administrator
  I need an account-scoped site before registering beacons
  So that inventory is not shared with other accounts

  Background:
    Given a clinic administrator registered as "beacon-admin@clinica.pe" with password "Rumbo2026"
    And they sign in as "beacon-admin@clinica.pe" with password "Rumbo2026"

  Scenario: A clinic administrator registers a new site
    When I send an authenticated POST request to "/api/v1/sites" with body:
      """
      {"name":"Clinica Norte"}
      """
    Then the response status is 201
    And the response field "/id" is present
    And the response field "/name" is "Clinica Norte"
