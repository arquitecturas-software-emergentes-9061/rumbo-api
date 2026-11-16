Feature: PR-4 Request a demo or onboarding from the landing page
  As the person in charge of a clinic who does not have an account yet
  I want to leave my contact data on the landing page
  So that the Rumbo team can show me a demo or start my onboarding

  Scenario: A demo request is stored and answered with its identifier
    When I send a POST request to "/api/v1/onboarding-requests" with body:
      """
      {"type":"DEMO","fullName":"Carmen Mendoza","email":"carmen@clinica.pe","clinicName":"Clinica San Lucas",
       "role":"Directora de TI","sitesRange":"TWO_TO_THREE","message":"Queremos probar Rumbo",
       "privacyConsent":true}
      """
    Then the response status is 201
    And the response field "/id" is present
    And the response field "/type" is "DEMO"
    And the response does not contain "carmen@clinica.pe"

  Scenario: An onboarding request without the optional fields is accepted
    When I send a POST request to "/api/v1/onboarding-requests" with body:
      """
      {"type":"ONBOARDING","fullName":"Jorge Paredes","email":"jorge@clinica.pe","clinicName":"Clinica Norte",
       "role":"Gerente de Operaciones","privacyConsent":true}
      """
    Then the response status is 201
    And the response field "/type" is "ONBOARDING"

  Scenario: Without the privacy consent the request is rejected
    When I send a POST request to "/api/v1/onboarding-requests" with body:
      """
      {"type":"DEMO","fullName":"Carmen Mendoza","email":"carmen@clinica.pe","clinicName":"Clinica San Lucas",
       "role":"Directora de TI","privacyConsent":false}
      """
    Then the response status is 400
    And the response field "/code" is "VALIDATION_FAILED"
    And the response field "/errors/privacyConsent" is present
