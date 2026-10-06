@products @regression
Feature: Products and Cart

  Background:
    Given the user is on the login page
    And the user logs in with username "standard_user" and password "secret_sauce"

  @smoke
  Scenario: All products are displayed
    Then the user should see 6 products listed

  Scenario: Add products to the cart
    When the user adds 2 products to the cart
    Then the cart badge should show "2"

  # Intentionally failing scenario to demonstrate the failure log, screenshot and video.
  # Exclude it with:  mvn test -Dcucumber.filter.tags="not @demoFailure"
  @demoFailure
  Scenario: Demo failure - wrong cart count
    When the user adds 1 product to the cart
    Then the cart badge should show "5"
    And the user should see 6 products listed
