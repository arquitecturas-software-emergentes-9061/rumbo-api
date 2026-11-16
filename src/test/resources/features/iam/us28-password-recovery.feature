Feature: US-28 Recover access to the account
  As a user who forgot the password
  I want to receive a link to choose a new one
  So that I can get back into Rumbo without anyone learning which emails are registered

  Background:
    Given a clinic administrator registered as "ana@clinica.pe" with password "Rumbo2026"

  Scenario: The answer is the same whether or not the email is registered
    When they request a password recovery for "ana@clinica.pe"
    Then the response status is 202
    When they request a password recovery for "nobody@clinica.pe"
    Then the response status is 202
    And no recovery link was sent to "nobody@clinica.pe"

  Scenario: The emailed link sets a new password
    Given they sign in as "ana@clinica.pe" with password "Rumbo2026"
    When they request a password recovery for "ana@clinica.pe"
    And they open the link sent to "ana@clinica.pe" and choose the password "Rumbo2027"
    Then the response status is 204
    When I send an authenticated GET request to "/api/v1/user-accounts/me"
    Then the response status is 401
    When they sign in as "ana@clinica.pe" with password "Rumbo2026"
    Then the response status is 401
    When they sign in as "ana@clinica.pe" with password "Rumbo2027"
    Then the response status is 200

  Scenario: A link works only once
    When they request a password recovery for "ana@clinica.pe"
    And they open the link sent to "ana@clinica.pe" and choose the password "Rumbo2027"
    And they open the same link again and choose the password "Rumbo2028"
    Then the response status is 400
    And the response field "/code" is "INVALID_PASSWORD_RESET_TOKEN"

  Scenario: A weak password is rejected without using up the link
    When they request a password recovery for "ana@clinica.pe"
    And they open the link sent to "ana@clinica.pe" and choose the password "short"
    Then the response status is 400
    And the response field "/code" is "WEAK_PASSWORD"
    When they open the same link again and choose the password "Rumbo2027"
    Then the response status is 204
