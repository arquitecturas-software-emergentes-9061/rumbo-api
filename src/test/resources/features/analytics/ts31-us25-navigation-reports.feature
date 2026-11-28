Feature: TS-31 and US-25 Navigation feedback and reports for the clinic administrator
  As a clinic administrator
  I want to read the ratings, incident reports, trends and incident zones of my sites
  So that I can find where visitors get lost and improve the signage

  # Reading a site needs its ownership, which lives in the postgres profile. Those scenarios (consolidated feedback,
  # repeated events, trends by day, incident zones, overview of the caller's sites and site separation) are covered
  # by NavigationFeedbackInsightsIntegrationTest and JdbcNavigationInsightsReaderIntegrationTest.

  Scenario: The reports are not public
    When I send a GET request to "/api/v1/analytics/feedback-summary?siteId=9d6f2b7e-1c3a-4e5f-8a9b-0c1d2e3f4a5b&from=2026-11-01T05:00:00Z&to=2026-11-08T05:00:00Z"
    Then the response status is 401

  Scenario: A patient cannot read the reports of a site
    Given a patient registered as "rosa@mail.com" with password "Rumbo2026"
    And they sign in as "rosa@mail.com" with password "Rumbo2026"
    When I send an authenticated GET request to "/api/v1/analytics/incident-zones?siteId=9d6f2b7e-1c3a-4e5f-8a9b-0c1d2e3f4a5b&from=2026-11-01T05:00:00Z&to=2026-11-08T05:00:00Z"
    Then the response status is 403

  Scenario: A patient has no sites to compare
    Given a patient registered as "mario@mail.com" with password "Rumbo2026"
    And they sign in as "mario@mail.com" with password "Rumbo2026"
    When I send an authenticated GET request to "/api/v1/analytics/sites-overview?from=2026-11-01T05:00:00Z&to=2026-11-08T05:00:00Z"
    Then the response status is 403
