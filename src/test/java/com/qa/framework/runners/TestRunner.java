package com.qa.framework.runners;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

/**
 * Main runner. Each scenario is a DataProvider row; parallel = true runs them
 * concurrently (thread count comes from -Dthreads, see pom.xml).
 *
 * Run a subset by tag:  mvn test -Dcucumber.filter.tags="@smoke"
 */
@CucumberOptions(
        features = "src/test/resources/features",
        glue = {"com.qa.framework.stepdefinitions", "com.qa.framework.hooks"},
        plugin = {
                "summary",
                "com.qa.framework.listeners.FailedScenarioLogger",
                "rerun:target/failed_scenarios.txt"
        },
        tags = "not @ignore",
        monochrome = true
)
public class TestRunner extends AbstractTestNGCucumberTests {

    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
