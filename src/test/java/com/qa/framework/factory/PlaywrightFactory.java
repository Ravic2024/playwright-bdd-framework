package com.qa.framework.factory;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.qa.framework.config.ConfigReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;

/**
 * Creates and tears down the Playwright stack for ONE scenario.
 *
 * Playwright objects are not thread-safe, so every scenario gets its own
 * Playwright -> Browser -> BrowserContext -> Page chain. PicoContainer creates a new
 * TestContext (and therefore a new PlaywrightFactory) per scenario, which keeps
 * parallel threads fully isolated without any ThreadLocal.
 */
public class PlaywrightFactory {

    private static final Logger LOG = LogManager.getLogger(PlaywrightFactory.class);

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    public Page initBrowser() {
        String browserName = ConfigReader.get("browser").toLowerCase();
        boolean headless = ConfigReader.getBoolean("headless");
        int width = ConfigReader.getInt("viewport.width");
        int height = ConfigReader.getInt("viewport.height");

        LOG.info("Launching '{}' (headless={})", browserName, headless);
        playwright = Playwright.create();

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setSlowMo(ConfigReader.getInt("slow.mo"));

        browser = switch (browserName) {
            case "firefox" -> playwright.firefox().launch(launchOptions);
            case "webkit" -> playwright.webkit().launch(launchOptions);
            case "chrome" -> playwright.chromium().launch(launchOptions.setChannel("chrome"));
            case "msedge" -> playwright.chromium().launch(launchOptions.setChannel("msedge"));
            case "chromium" -> playwright.chromium().launch(launchOptions);
            default -> throw new IllegalArgumentException("Unsupported browser: " + browserName);
        };

        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setViewportSize(width, height);

        // Every scenario is recorded; Hooks keeps the video only if the scenario fails.
        if (ConfigReader.getBoolean("video.enabled")) {
            contextOptions
                    .setRecordVideoDir(Paths.get(ConfigReader.get("video.temp.dir")))
                    .setRecordVideoSize(width, height);
        }

        context = browser.newContext(contextOptions);
        context.setDefaultTimeout(ConfigReader.getInt("default.timeout"));
        context.setDefaultNavigationTimeout(ConfigReader.getInt("navigation.timeout"));

        page = context.newPage();
        return page;
    }

    public Page getPage() {
        if (page == null) {
            throw new IllegalStateException("Browser not initialised. Did the @Before hook run?");
        }
        return page;
    }

    public boolean isPageAvailable() {
        return page != null && !page.isClosed();
    }

    /** Closing the context finalises the video file on disk. */
    public void closeContext() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    public void quit() {
        try {
            closeContext();
            if (browser != null) {
                browser.close();
            }
        } finally {
            if (playwright != null) {
                playwright.close();
            }
            browser = null;
            playwright = null;
            page = null;
        }
    }
}
