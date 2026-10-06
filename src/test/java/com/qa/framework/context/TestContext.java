package com.qa.framework.context;

import com.qa.framework.factory.PlaywrightFactory;
import com.qa.framework.pages.PageObjectManager;

/**
 * The single object PicoContainer injects into Hooks and every Step Definition class.
 *
 * Pico creates ONE instance per scenario and shares it among all classes that ask for it
 * in their constructor, then discards it when the scenario ends. That gives us:
 *  - one browser per scenario (safe for parallel runs)
 *  - shared page objects and scenario data across step classes
 */
public class TestContext {

    private final PlaywrightFactory playwrightFactory = new PlaywrightFactory();
    private final ScenarioContext scenarioContext = new ScenarioContext();
    private PageObjectManager pageObjectManager;

    public PlaywrightFactory getPlaywrightFactory() {
        return playwrightFactory;
    }

    public ScenarioContext getScenarioContext() {
        return scenarioContext;
    }

    public PageObjectManager getPages() {
        if (pageObjectManager == null) {
            pageObjectManager = new PageObjectManager(playwrightFactory.getPage());
        }
        return pageObjectManager;
    }
}
