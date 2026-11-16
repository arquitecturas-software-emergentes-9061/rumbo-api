Feature: TS-01 Sign in with email and password
  As a registered user
  I want to sign in with my credentials
  So that I can use the protected features of Rumbo

  Background:
    Given a clinic administrator registered as "ana@clinica.pe" with password "Rumbo2026"

  Scenario: Valid credentials open a session
    When they sign in as "ana@clinica.pe" with password "Rumbo2026"
    Then the response status is 200
    And the response field "/tokenType" is "Bearer"
    And the response field "/accessToken" is present
    And the response field "/refreshToken" is present
    When I send an authenticated GET request to "/api/v1/user-accounts/me"
    Then the response status is 200
    And the response email is "ana@clinica.pe"

  Scenario: Wrong password gets a generic answer
    When they sign in as "ana@clinica.pe" with password "Wrong2026"
    Then the response status is 401
    And the response field "/code" is "INVALID_CREDENTIALS"

  Scenario: Protected resources require a token
    When I send a GET request to "/api/v1/user-accounts/me"
    Then the response status is 401
    And the response field "/code" is "AUTHENTICATION_REQUIRED"

  Scenario: Repeated failures lock the account
    When they fail to sign in as "ana@clinica.pe" 5 times
    Then the response status is 423
    And the response field "/code" is "ACCOUNT_LOCKED"
    When they sign in as "ana@clinica.pe" with password "Rumbo2026"
    Then the response status is 423
