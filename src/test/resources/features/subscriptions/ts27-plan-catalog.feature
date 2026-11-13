Feature: Monthly subscription plan catalog (TS-27)
  Scenario: Clinic administrator sees the monthly catalog in display order
    Given a clinic administrator registered as "catalog-admin@example.test" with password "Catalog-Test-79"
    When they sign in as "catalog-admin@example.test" with password "Catalog-Test-79"
    And I send an authenticated GET request to "/api/v1/subscriptions/plans"
    Then the response status is 200
    And the response field "/0/code" is "BASIC"
    And the response field "/0/name" is "Básico"
    And the response field "/0/price/amount" is "39.9"
    And the response field "/0/price/currency" is "PEN"
    And the response field "/0/billingInterval" is "MONTH"
    And the response field "/0/purchaseMode" is "CHECKOUT"
    And the response field "/1/code" is "PROFESSIONAL"
    And the response field "/1/price/amount" is "49.99"
    And the response field "/1/billingInterval" is "MONTH"
    And the response field "/1/purchaseMode" is "CHECKOUT"
    And the response field "/2/code" is "ENTERPRISE"
    And the response field "/2/price/amount" is "59.99"
    And the response field "/2/billingInterval" is "MONTH"
    And the response field "/2/purchaseMode" is "CONTACT_SALES"
    And the response does not contain "stripe"

  Scenario: Patient cannot read the catalog
    Given a patient registered as "catalog-patient@example.test" with password "Catalog-Test-79"
    When they sign in as "catalog-patient@example.test" with password "Catalog-Test-79"
    And I send an authenticated GET request to "/api/v1/subscriptions/plans"
    Then the response status is 403

  Scenario: Anonymous visitor cannot read the catalog
    When I send a GET request to "/api/v1/subscriptions/plans"
    Then the response status is 401
