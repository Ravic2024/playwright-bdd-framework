@login @regression
Feature: Login
  As a registered user
  I want to log in to the application
  So that I can browse products

  Background:
    Given the user is on the login page

  @smoke
  Scenario: Successful login with valid credentials
    When the user logs in with username "standard_user" and password "secret_sauce"
    Then the user should land on the "Products" page

  Scenario: Locked out user cannot log in
    When the user logs in with username "locked_out_user" and password "secret_sauce"
    Then the user should see the error message "Sorry, this user has been locked out."

  @excel
  Scenario Outline: Data driven login from Excel - <TestCaseId>
    When the user logs in using Excel test data "<TestCaseId>"
    Then the login result should match the expected result in Excel

    Examples:
      | TestCaseId |
      | TC_01      |
      | TC_02      |
      | TC_03      |
      | TC_04      |
