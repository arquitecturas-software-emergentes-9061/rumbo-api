Feature: Subscription checkout payment orders (PR-80)
  Scenario: Enterprise checkout requires sales contact
    Given a clinic administrator registered as "checkout-sales@example.test" with password "Checkout-Test-80"
    When they sign in as "checkout-sales@example.test" with password "Checkout-Test-80"
    And I send an authenticated POST request to "/api/v1/subscriptions/checkout-sessions" with body:
      """
      {"planCode":"ENTERPRISE"}
      """
    Then the response status is 409
    And the response field "/code" is "PLAN_REQUIRES_SALES_CONTACT"

  Scenario: Patient cannot create a checkout session
    Given a patient registered as "checkout-patient@example.test" with password "Checkout-Test-80"
    When they sign in as "checkout-patient@example.test" with password "Checkout-Test-80"
    And I send an authenticated POST request to "/api/v1/subscriptions/checkout-sessions" with body:
      """
      {"planCode":"BASIC"}
      """
    Then the response status is 403

  Scenario: Anonymous visitor cannot create a checkout session
    When I send a POST request to "/api/v1/subscriptions/checkout-sessions" with body:
      """
      {"planCode":"BASIC"}
      """
    Then the response status is 401
