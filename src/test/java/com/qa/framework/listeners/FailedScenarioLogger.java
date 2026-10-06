package com.qa.framework.listeners;

import com.qa.framework.utils.FailureArtifacts;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.HookTestStep;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestStepFinished;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cucumber plugin that writes to test-output/logs/failed-scenarios.log ONLY for failed scenarios.
 *
 * For each failed scenario it prints:
 *   - feature file, line, tags, thread
 *   - every step that ran, in order, with its status, up to and including the failed step
 *   - the error message (and the top of the stack trace) right after the failed step
 *   - screenshot / video paths
 *
 * Steps are buffered per test case (keyed by its UUID), so parallel threads never mix.
 * Each failure block is logged with one call, so blocks never interleave in the file.
 */
public class FailedScenarioLogger implements ConcurrentEventListener {

    private static final Logger FAILED_LOG = LogManager.getLogger("FailedScenarioLogger");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int STACK_LINES = 8;
    private static final String LINE = "=".repeat(100);

    private final Map<UUID, ScenarioRecord> records = new ConcurrentHashMap<>();

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, this::onTestCaseStarted);
        publisher.registerHandlerFor(TestStepFinished.class, this::onTestStepFinished);
        publisher.registerHandlerFor(TestCaseFinished.class, this::onTestCaseFinished);
    }

    private void onTestCaseStarted(TestCaseStarted event) {
        records.put(event.getTestCase().getId(), new ScenarioRecord(Thread.currentThread().getName()));
    }

    private void onTestStepFinished(TestStepFinished event) {
        ScenarioRecord record = records.get(event.getTestCase().getId());
        if (record == null || record.failedRecorded) {
            return; // stop recording once the failure is captured (later steps are skipped anyway)
        }
        Result result = event.getResult();
        Status status = result.getStatus();

        if (event.getTestStep() instanceof PickleStepTestStep step) {
            String text = String.format("  [%-7s] Line %-4d %s%s  (%d ms)",
                    status, step.getStep().getLine(), step.getStep().getKeyword(),
                    step.getStep().getText(), result.getDuration().toMillis());
            record.steps.add(text);
        } else if (event.getTestStep() instanceof HookTestStep hook && status == Status.FAILED) {
            record.steps.add(String.format("  [%-7s] %s hook -> %s",
                    status, hook.getHookType(), hook.getCodeLocation()));
        }

        if (status == Status.FAILED || status == Status.UNDEFINED || status == Status.PENDING) {
            record.failedRecorded = true;
            record.error = result.getError();
            record.failedStatus = status;
        }
    }

    private void onTestCaseFinished(TestCaseFinished event) {
        TestCase testCase = event.getTestCase();
        ScenarioRecord record = records.remove(testCase.getId());
        List<String> artifacts = FailureArtifacts.remove(testCase.getId().toString());

        Status finalStatus = event.getResult().getStatus();
        if (record == null || finalStatus == Status.PASSED || finalStatus == Status.SKIPPED) {
            return; // passed / skipped scenarios produce no log
        }
        FAILED_LOG.error(buildBlock(testCase, record, event.getResult(), artifacts));
    }

    private String buildBlock(TestCase testCase, ScenarioRecord record, Result finalResult, List<String> artifacts) {
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator()).append(LINE).append(System.lineSeparator());
        sb.append("FAILED SCENARIO : ").append(testCase.getName()).append(System.lineSeparator());
        sb.append("Feature file    : ").append(testCase.getUri()).append(':').append(testCase.getLocation().getLine()).append(System.lineSeparator());
        sb.append("Tags            : ").append(testCase.getTags()).append(System.lineSeparator());
        sb.append("Thread          : ").append(record.thread).append(System.lineSeparator());
        sb.append("Finished at     : ").append(LocalDateTime.now().format(TS)).append(System.lineSeparator());
        sb.append(LINE).append(System.lineSeparator());
        sb.append("Steps executed:").append(System.lineSeparator());
        record.steps.forEach(s -> sb.append(s).append(System.lineSeparator()));

        sb.append(System.lineSeparator()).append("ERROR (").append(record.failedStatus != null ? record.failedStatus : finalResult.getStatus()).append("):").append(System.lineSeparator());
        Throwable error = record.error != null ? record.error : finalResult.getError();
        if (error != null) {
            sb.append("  ").append(error.getClass().getName()).append(": ").append(error.getMessage()).append(System.lineSeparator());
            StackTraceElement[] trace = error.getStackTrace();
            for (int i = 0; i < Math.min(STACK_LINES, trace.length); i++) {
                sb.append("      at ").append(trace[i]).append(System.lineSeparator());
            }
            Throwable cause = error.getCause();
            if (cause != null && cause != error) {
                sb.append("  Caused by: ").append(cause.getClass().getName()).append(": ").append(cause.getMessage()).append(System.lineSeparator());
            }
        } else {
            sb.append("  Step is undefined or pending - implement the step definition.").append(System.lineSeparator());
        }

        if (!artifacts.isEmpty()) {
            sb.append(System.lineSeparator()).append("Evidence:").append(System.lineSeparator());
            artifacts.forEach(a -> sb.append("  ").append(a).append(System.lineSeparator()));
        }
        sb.append(LINE);
        return sb.toString();
    }

    private static final class ScenarioRecord {
        private final String thread;
        private final List<String> steps = Collections.synchronizedList(new ArrayList<>());
        private volatile boolean failedRecorded;
        private volatile Throwable error;
        private volatile Status failedStatus;

        private ScenarioRecord(String thread) {
            this.thread = thread;
        }
    }
}
