Feature: TS-30 Navigation telemetry ingestion
  As the mobile app
  I want to send the facts of each navigation session without waiting for the metrics
  So that the dashboard gets anonymized data without slowing down the visitor's trip

  # Scenarios 2 and 3 (summary and invalid date range) need the site ownership of the postgres profile and are
  # covered by NavigationSummaryIntegrationTest.

  Background:
    Given a patient registered as "luis@mail.com" with password "Rumbo2026"
    And they sign in as "luis@mail.com" with password "Rumbo2026"

  Scenario: Valid events of a session are accepted
    When I send an authenticated POST request to "/api/v1/analytics/navigation-events" with body:
      """
      {"events": [
        {"navigationSessionId": "4b0c1a52-3f7e-4d2a-9a51-6f3e2c1d0a01", "siteId": "9d6f2b7e-1c3a-4e5f-8a9b-0c1d2e3f4a5b",
         "eventType": "NAVIGATION_STARTED", "occurredAt": "2026-11-01T14:00:00Z",
         "metadata": {"destinationNodeReference": "cardiologia-201"}},
        {"navigationSessionId": "4b0c1a52-3f7e-4d2a-9a51-6f3e2c1d0a01", "siteId": "9d6f2b7e-1c3a-4e5f-8a9b-0c1d2e3f4a5b",
         "eventType": "NAVIGATION_FINISHED", "occurredAt": "2026-11-01T14:04:00Z",
         "metadata": {"result": "ARRIVED", "durationSeconds": 240}}
      ]}
      """
    Then the response status is 202

  Scenario: Personal data in the metadata is rejected
    When I send an authenticated POST request to "/api/v1/analytics/navigation-events" with body:
      """
      {"events": [
        {"navigationSessionId": "4b0c1a52-3f7e-4d2a-9a51-6f3e2c1d0a02", "siteId": "9d6f2b7e-1c3a-4e5f-8a9b-0c1d2e3f4a5b",
         "eventType": "NAVIGATION_STARTED", "occurredAt": "2026-11-01T14:00:00Z",
         "metadata": {"patientName": "Luis Rojas"}}
      ]}
      """
    Then the response status is 400
    And the response field "/code" is "VALIDATION_FAILED"
