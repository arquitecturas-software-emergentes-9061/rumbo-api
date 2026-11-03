Feature: Platform availability
  As a member of the Rumbo team
  I want a health endpoint backed by the real database and cache
  So that deployments can verify the API is ready before receiving traffic

  Scenario: The API reports that it is up
    When I send a GET request to "/actuator/health"
    Then the response status is 200
    And the response field "/status" is "UP"
