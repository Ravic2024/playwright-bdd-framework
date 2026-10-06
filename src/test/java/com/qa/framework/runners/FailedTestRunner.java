package com.qa.framework.runners;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

/**
 * Re-runs only the scenarios that failed in the last TestRunner execution.
 * Usage:  mvn test -Dsurefire.suiteXmlFiles=testng-rerun.xml
 */
@CucumberOptions(
        features = "@target/failed_scenarios.txt",
        glue = {"com.qa.framework.stepdefinitions", "com.qa.framework.hooks"},
        plugin = {"summary", "com.qa.framework.listeners.FailedScenarioLogger"},
        monochrome = true
)
public class FailedTestRunner extends AbstractTestNGCucumberTests {

    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
