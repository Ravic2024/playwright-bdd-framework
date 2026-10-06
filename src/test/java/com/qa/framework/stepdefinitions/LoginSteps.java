package com.qa.framework.stepdefinitions;

import com.qa.framework.config.ConfigReader;
import com.qa.framework.context.TestContext;
import com.qa.framework.pages.LoginPage;
import com.qa.framework.pages.ProductsPage;
import com.qa.framework.utils.ExcelUtil;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.testng.Assert;

import java.util.Map;

public class LoginSteps {

    private static final String SHEET = "LoginData";
    private static final String KEY_COLUMN = "TestCaseId";
    private static final String CTX_TEST_DATA = "loginTestData";

    private final TestContext testContext;

    // Constructor injection by PicoContainer
    public LoginSteps(TestContext testContext) {
        this.testContext = testContext;
    }

    private LoginPage loginPage() {
        return testContext.getPages().loginPage();
    }

    private ProductsPage productsPage() {
        return testContext.getPages().productsPage();
    }

    @Given("the user is on the login page")
    public void theUserIsOnTheLoginPage() {
        loginPage().open();
    }

    @When("the user logs in with username {string} and password {string}")
    public void theUserLogsInWith(String username, String password) {
        loginPage().login(username, password);
    }

    @When("the user logs in using Excel test data {string}")
    public void theUserLogsInUsingExcelData(String testCaseId) {
        Map<String, String> data = ExcelUtil.getRowData(
                ConfigReader.get("excel.path"), SHEET, KEY_COLUMN, testCaseId);
        testContext.getScenarioContext().set(CTX_TEST_DATA, data);
        loginPage().login(data.get("Username"), data.get("Password"));
    }

    @Then("the login result should match the expected result in Excel")
    public void theLoginResultShouldMatchExpected() {
        Map<String, String> data = testContext.getScenarioContext().get(CTX_TEST_DATA);
        String expected = data.get("ExpectedResult");
        String actual = "SUCCESS".equalsIgnoreCase(expected)
                ? productsPage().getHeader()
                : loginPage().getErrorMessage();

        String status = "SUCCESS".equalsIgnoreCase(expected)
                ? ("Products".equals(actual) ? "PASS" : "FAIL")
                : (actual.contains(expected) ? "PASS" : "FAIL");

        // Write back to Excel (thread-safe, runs in parallel with other scenarios)
        String excel = ConfigReader.get("excel.path");
        String id = data.get(KEY_COLUMN);
        ExcelUtil.writeCellData(excel, SHEET, KEY_COLUMN, id, "ActualResult", actual);
        ExcelUtil.writeCellData(excel, SHEET, KEY_COLUMN, id, "Status", status);

        Assert.assertEquals(status, "PASS",
                "Login result mismatch for " + id + " -> expected: '" + expected + "', actual: '" + actual + "'");
    }

    @Then("the user should land on the {string} page")
    public void theUserShouldLandOnPage(String expectedHeader) {
        Assert.assertEquals(productsPage().getHeader(), expectedHeader, "Page header mismatch");
    }

    @Then("the user should see the error message {string}")
    public void theUserShouldSeeErrorMessage(String expectedError) {
        String actual = loginPage().getErrorMessage();
        Assert.assertTrue(actual.contains(expectedError),
                "Expected error containing '" + expectedError + "' but was '" + actual + "'");
    }
}
