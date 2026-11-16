Feature: TS-33 Initialize profiles after account registration
  Scenario: Patient profile starts with registration data and no phone or image
    Given a patient registered as "profile-patient@example.com" with password "Rumbo2026"
    Then the response status is 201
    And the registered user has exactly one initial profile named "Luis Rojas" with role "PATIENT"

  Scenario: Administrator profile ignores clinic data
    When a clinic administrator signs up as "profile-admin@example.com" with password "Rumbo2026"
    Then the response status is 201
    And the registered user has exactly one initial profile named "Ana Torres" with role "CLINIC_ADMIN"
