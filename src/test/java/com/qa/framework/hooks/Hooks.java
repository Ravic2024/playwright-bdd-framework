package com.qa.framework.hooks;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Video;
import com.qa.framework.config.ConfigReader;
import com.qa.framework.context.TestContext;
import com.qa.framework.factory.PlaywrightFactory;
import com.qa.framework.utils.FailureArtifacts;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Hooks {

    private static final Logger LOG = LogManager.getLogger(Hooks.class);

    private final TestContext testContext;

    // PicoContainer injects the same TestContext the step definitions receive
    public Hooks(TestContext testContext) {
        this.testContext = testContext;
    }

    @Before(order = 0)
    public void setUp(Scenario scenario) {
        LOG.info("START  >> {}", scenario.getName());
        testContext.getPlaywrightFactory().initBrowser();
    }

    @After(order = 0)
    public void tearDown(Scenario scenario) {
        PlaywrightFactory factory = testContext.getPlaywrightFactory();
        boolean failed = scenario.isFailed();
        String fileName = FailureArtifacts.fileName(scenario.getName());
        Video video = null;

        try {
            if (factory.isPageAvailable()) {
                Page page = factory.getPage();
                video = page.video();
                if (failed) {
                    captureScreenshot(scenario, page, fileName);
                }
            }
            // Video is written to disk only when the context closes
            factory.closeContext();
            if (video != null) {
                handleVideo(scenario, video, failed, fileName);
            }
        } catch (Exception e) {
            LOG.error("Problem while collecting failure evidence for '{}': {}", scenario.getName(), e.getMessage());
        } finally {
            factory.quit();
            LOG.info("FINISH << {} [{}]", scenario.getName(), scenario.getStatus());
        }
    }

    private void captureScreenshot(Scenario scenario, Page page, String fileName) {
        try {
            Path path = Paths.get(ConfigReader.get("screenshot.dir"), fileName + ".png");
            byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(true));
            scenario.attach(screenshot, "image/png", scenario.getName());
            FailureArtifacts.add(scenario.getId(), "Screenshot : " + path.toAbsolutePath());
            LOG.info("Screenshot saved: {}", path);
        } catch (Exception e) {
            LOG.error("Could not capture screenshot: {}", e.getMessage());
        }
    }

    /** Failed scenario -> keep the video. Passed scenario -> delete it. */
    private void handleVideo(Scenario scenario, Video video, boolean failed, String fileName) {
        try {
            if (failed) {
                Path path = Paths.get(ConfigReader.get("video.dir"), fileName + ".webm");
                video.saveAs(path);
                FailureArtifacts.add(scenario.getId(), "Video      : " + path.toAbsolutePath());
                LOG.info("Video saved: {}", path);
            }
            video.delete(); // removes the temp recording in both cases
        } catch (Exception e) {
            LOG.error("Could not process video: {}", e.getMessage());
        }
    }
}
