Feature: TS-26 Register an account
  As a clinic administrator
  I want to create my Rumbo account
  So that I can manage the indoor maps of my clinic

  Scenario: Successful registration
    When a clinic administrator signs up as "ana@clinica.pe" with password "Rumbo2026"
    Then the response status is 201
    And the response email is "ana@clinica.pe"
    And the response field "/status" is "ACTIVE"
    And the response does not contain "Rumbo2026"

  Scenario: Email already registered
    Given a clinic administrator registered as "ana@clinica.pe" with password "Rumbo2026"
    When a clinic administrator signs up as "ana@clinica.pe" with password "Rumbo2026"
    Then the response status is 409
    And the response field "/code" is "EMAIL_ALREADY_REGISTERED"

  Scenario: Invalid data is reported field by field
    When I send a POST request to "/api/v1/auth/sign-up" with body:
      """
      {"email":"not-an-email","password":"short","role":"CLINIC_ADMIN","fullName":"","acceptedTerms":false}
      """
    Then the response status is 400
    And the response field "/code" is "VALIDATION_FAILED"
    And the response field "/errors/email" is present
    And the response field "/errors/fullName" is present
    And the response field "/errors/acceptedTerms" is present

  Scenario: Password below the policy
    When a clinic administrator signs up as "ana@clinica.pe" with password "rumbo"
    Then the response status is 400
    And the response field "/code" is "WEAK_PASSWORD"
