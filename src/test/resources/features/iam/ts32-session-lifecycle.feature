Feature: TS-32 Session lifecycle
  As a signed-in user
  I want my session to renew safely and to end when I sign out
  So that a stolen or forgotten token cannot be used against my account

  Background:
    Given a clinic administrator registered as "ana@clinica.pe" with password "Rumbo2026"
    And they sign in as "ana@clinica.pe" with password "Rumbo2026"

  Scenario: Refreshing rotates the refresh token
    When they refresh the session
    Then the response status is 200
    And the response field "/refreshToken" is present
    When they refresh with the previous refresh token
    Then the response status is 401
    And the response field "/code" is "INVALID_REFRESH_TOKEN"

  Scenario: Signing out invalidates the access token at once
    When they sign out
    Then the response status is 204
    When I send an authenticated GET request to "/api/v1/user-accounts/me"
    Then the response status is 401

  Scenario: Signing out invalidates the refresh token
    When they sign out
    And they refresh the session
    Then the response status is 401
