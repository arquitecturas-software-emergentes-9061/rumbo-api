Feature: TS-24 Authorization of administrative operations by site
  As the owner of a clinic site
  I want administrative operations on my site to be limited to my account
  So that nobody else can read or change its plans, graphs or beacons

  Planning graphs and map publication are covered with the postgres profile in SiteAuthorizationIntegrationTest.

  Background:
    Given a clinic administrator registered as "owner@clinica.pe" with password "Rumbo2026"
    And a clinic administrator registered as "intruder@clinica.pe" with password "Rumbo2026"
    And a patient registered as "patient@mail.com" with password "Rumbo2026"
    And they sign in as "owner@clinica.pe" with password "Rumbo2026"
    And I send an authenticated POST request to "/api/v1/sites" with body:
      """
      {"name":"Sede Central"}
      """
    And I remember the response field "/id" as "siteId"

  Scenario: The owner manages its own site
    When I send an authenticated GET request to "/api/v1/sites/{siteId}/beacons"
    Then the response status is 200

  Scenario: An anonymous request is rejected
    When I send a GET request to "/api/v1/sites/{siteId}/beacons"
    Then the response status is 401
    And the response field "/code" is "AUTHENTICATION_REQUIRED"

  Scenario: An administrator of another site cannot see it or change it
    When they sign in as "intruder@clinica.pe" with password "Rumbo2026"
    And I send an authenticated GET request to "/api/v1/sites/{siteId}/beacons"
    Then the response status is 404
    When I send an authenticated POST request to "/api/v1/sites/{siteId}/beacons" with body:
      """
      {"identifier":"AA:BB:CC:01","floorId":"00000000-0000-4000-8000-000000000002","x":1.0,"y":1.0}
      """
    Then the response status is 404
    When they sign in as "owner@clinica.pe" with password "Rumbo2026"
    And I send an authenticated GET request to "/api/v1/sites/{siteId}/beacons"
    Then the response status is 200
    And the response body is an empty list

  Scenario: A patient cannot run administrative operations
    When they sign in as "patient@mail.com" with password "Rumbo2026"
    And I send an authenticated GET request to "/api/v1/sites/{siteId}/beacons"
    Then the response status is 403
    And the response field "/code" is "ACCESS_DENIED"
