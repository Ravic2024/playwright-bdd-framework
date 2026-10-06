# Playwright + Java + Cucumber BDD Framework

Playwright (Java) · Cucumber 7 · TestNG · PicoContainer DI · Page Object Model · Parallel execution ·
Thread-safe Excel read/write · Screenshot + video on failure · Failure-only step log.

Demo application: https://www.saucedemo.com (public practice site).

## Project structure

```
playwright-bdd-framework
├── pom.xml
├── testng.xml                     # main suite
├── testng-rerun.xml               # re-run only failed scenarios
└── src/test
    ├── java/com/qa/framework
    │   ├── config/ConfigReader.java          # config.properties + -D / env overrides
    │   ├── context/TestContext.java          # injected by PicoContainer (one per scenario)
    │   ├── context/ScenarioContext.java      # share data between step classes
    │   ├── factory/PlaywrightFactory.java    # Playwright -> Browser -> Context -> Page
    │   ├── pages/BasePage, LoginPage, ProductsPage, PageObjectManager
    │   ├── hooks/Hooks.java                  # @Before launch, @After screenshot/video/close
    │   ├── listeners/FailedScenarioLogger.java  # Cucumber plugin: failure-only step log
    │   ├── stepdefinitions/LoginSteps, ProductSteps
    │   ├── runners/TestRunner.java           # parallel DataProvider
    │   ├── runners/FailedTestRunner.java     # reruns target/failed_scenarios.txt
    │   └── utils/ExcelUtil.java, FailureArtifacts.java
    └── resources
        ├── config/config.properties
        ├── features/Login.feature, Products.feature
        ├── testdata/TestData.xlsx            # sheet "LoginData"
        └── log4j2.xml
```

## How to run

```bash
mvn clean test                                        # all scenarios, 3 threads
mvn clean test -Dthreads=5                            # 5 parallel threads
mvn clean test -Dbrowser=firefox -Dheadless=false     # override any config key
mvn clean test -Dcucumber.filter.tags="@smoke"
mvn clean test -Dcucumber.filter.tags="not @demoFailure"
mvn test -Dsurefire.suiteXmlFiles=testng-rerun.xml    # rerun last failures
```

The first run downloads the Playwright browsers automatically (~300 MB).
`@demoFailure` in Products.feature fails **on purpose** so you can see the failure log,
screenshot and video. Delete it or exclude the tag once you've seen it working.

## Output (test-output/)

| Path | Written when |
|---|---|
| `logs/framework.log` | every run |
| `logs/failed-scenarios.log` | **only** if a scenario fails |
| `screenshots/<scenario>_<time>_<thread>.png` | failed scenarios only |
| `videos/failed/<scenario>_<time>_<thread>.webm` | failed scenarios only |

Sample `failed-scenarios.log` entry:

```
====================================================================================================
FAILED SCENARIO : Demo failure - wrong cart count
Feature file    : file:///.../features/Products.feature:20
Tags            : [@products, @regression, @demoFailure]
Thread          : TestNG-PoolService-2
Finished at     : 2026-10-05 23:41:12
====================================================================================================
Steps executed:
  [PASSED ] Line 5    Given the user is on the login page  (1840 ms)
  [PASSED ] Line 6    And the user logs in with username "standard_user" and password "secret_sauce"  (612 ms)
  [PASSED ] Line 21   When the user adds 1 product to the cart  (95 ms)
  [FAILED ] Line 22   Then the cart badge should show "5"  (14 ms)

ERROR (FAILED):
  java.lang.AssertionError: Cart badge count mismatch expected [5] but found [1]
      at org.testng.Assert.fail(Assert.java:111)
      ...
      at com.qa.framework.stepdefinitions.ProductSteps.theCartBadgeShouldShow(ProductSteps.java:36)

Evidence:
  Screenshot : /.../test-output/screenshots/Demo_failure_-_wrong_cart_count_20261005_234112_431_27.png
  Video      : /.../test-output/videos/failed/Demo_failure_-_wrong_cart_count_20261005_234112_431_27.webm
====================================================================================================
```

## How the key pieces work

**Dependency Injection (PicoContainer).** `Hooks`, `LoginSteps` and `ProductSteps` each take
`TestContext` in their constructor. Pico creates one `TestContext` per scenario and hands the same
instance to all of them, then throws it away at the end of the scenario.

**Parallel safety.** Each scenario has its own `TestContext` → its own `PlaywrightFactory` → its own
Playwright, Browser, Context and Page. Nothing browser-related is static, so threads never share a page.
Thread count comes from `-Dthreads` (pom → surefire → TestNG `dataproviderthreadcount`).

**Excel in parallel.** `ExcelUtil` keeps one `ReentrantReadWriteLock` per workbook file: many threads
can read together, a write is exclusive. Every call opens and closes its own workbook (nothing cached),
and writes go to a temp file that is moved over the original, so the file is never half-written.
Write-back: the Excel scenario fills the `ActualResult` and `Status` columns
(columns are auto-created if missing).
Note: the lock protects threads inside one JVM — keep the default single surefire fork.

**Screenshot + video.** Every context records video to a temp folder. In `@After`, if the scenario failed,
a full-page screenshot is saved (and attached to the Cucumber scenario), the context is closed to finalise
the video, and the video is saved to `videos/failed`. Passed scenarios' videos are deleted.

**Failure-only log.** `FailedScenarioLogger` is a Cucumber `ConcurrentEventListener` plugin. It buffers
steps per test case (by UUID), stops at the first failed/undefined/pending step, and on
`TestCaseFinished` writes the whole block in a single log call — so parallel failures never interleave.
Passed scenarios write nothing to this file.

## Adding a new page / feature

1. Create `XyzPage extends BasePage` with its locators and actions.
2. Add a lazy getter in `PageObjectManager`.
3. Create `XyzSteps` with a `TestContext` constructor and use `testContext.getPages().xyzPage()`.
4. Write the `.feature` file under `src/test/resources/features`.
