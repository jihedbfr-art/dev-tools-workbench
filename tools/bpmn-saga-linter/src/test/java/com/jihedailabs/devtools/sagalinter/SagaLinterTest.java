package com.jihedailabs.devtools.sagalinter;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class SagaLinterTest {

    private File testResource(String name) {
        return Paths.get("src/test/resources", name).toFile();
    }

    @Test
    void flagsBoundaryTimerThatBypassesCompensation() throws Exception {
        LintResult result = SagaLinter.lint(testResource("timer-skips-compensation.bpmn"));

        assertTrue(result.hasCompensationNode());
        assertTrue(result.hasHardFailure(), "a timer routed straight to an end event must be a hard failure");

        LintResult.TimerFinding timer = result.timerFindings().stream()
                .filter(f -> f.timerId().equals("slaTimeout"))
                .findFirst()
                .orElseThrow();
        assertFalse(timer.reachesCompensation());
    }

    @Test
    void acceptsTimerThatConvergesOnCompensation() throws Exception {
        LintResult result = SagaLinter.lint(testResource("timer-reaches-compensation.bpmn"));

        assertTrue(result.hasCompensationNode());
        assertFalse(result.hasHardFailure());
        assertTrue(result.timerFindings().stream().allMatch(LintResult.TimerFinding::reachesCompensation));
    }

    @Test
    void skipsProcessesThatDoNotModelCompensationAtAll() throws Exception {
        // Not every BPMN file is a saga. A process with no compensation task shouldn't be
        // reported as broken just because it also has a timer — there'd be nothing to reach.
        LintResult result = SagaLinter.lint(testResource("no-compensation.bpmn"));

        assertFalse(result.hasCompensationNode());
        assertFalse(result.hasHardFailure());
        assertTrue(result.timerFindings().isEmpty(), "timers must not be checked when there is no compensation to reach");
    }

    @Test
    void realNumberPortabilitySagaPasses() throws Exception {
        // The sibling bpmn-provisioning-patterns checkout is the reference "correct" saga: its
        // SLA timeout and its explicit-rejection branch deliberately converge on the same
        // compensation task. If this linter flags it, the linter is wrong, not the saga.
        Path saga = Paths.get("../../../bpmn-provisioning-patterns/src/main/resources/processes/number-portability-saga.bpmn");
        Assumptions.assumeTrue(Files.exists(saga),
                "bpmn-provisioning-patterns not found at " + saga.toAbsolutePath()
                        + " — clone it as a sibling of dev-tools-workbench to exercise this test");

        LintResult result = SagaLinter.lint(saga.toFile());

        assertTrue(result.hasCompensationNode(), "the real saga does have a compensation task");
        assertFalse(result.hasHardFailure(), "the real saga's SLA timer converges on compensation — no false positive allowed");
    }

    @Test
    void realBulkSimProvisioningPasses() throws Exception {
        // Same repo, different shape: this one has a compensation task but no boundary timer at
        // all. It must come back clean rather than tripping the gateway heuristic into an error.
        Path batch = Paths.get("../../../bpmn-provisioning-patterns/src/main/resources/processes/bulk-sim-provisioning.bpmn");
        Assumptions.assumeTrue(Files.exists(batch),
                "bpmn-provisioning-patterns not found at " + batch.toAbsolutePath());

        LintResult result = SagaLinter.lint(batch.toFile());

        assertTrue(result.hasCompensationNode());
        assertTrue(result.timerFindings().isEmpty(), "this process has no boundary timers");
        assertFalse(result.hasHardFailure());
    }

    @Test
    void gatewayWithoutCompensationBranchIsInformationalNotAFailure() throws Exception {
        // The linter can't read condition expressions, so it can't know which branch is the
        // failure path. A gateway with no compensating branch is surfaced as a warning only —
        // it must never flip the exit code on its own.
        LintResult result = SagaLinter.lint(testResource("timer-reaches-compensation.bpmn"));

        assertFalse(result.hasHardFailure());
    }
}
