package com.jihedailabs.devtools.sagalinter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SagaLinter {

    public static LintResult lint(File bpmnFile) throws Exception {
        BpmnGraph graph = BpmnGraph.parse(bpmnFile);
        boolean hasCompensation = graph.hasAnyCompensationNode();

        List<LintResult.TimerFinding> timerFindings = new ArrayList<>();
        // Rule A (hard failure): if this process models compensation at all, every boundary
        // timer must be able to reach it. A process with no compensation node anywhere isn't
        // a saga in the sense this linter cares about, so timers are left unchecked rather than
        // guessed at.
        if (hasCompensation) {
            for (String timerId : graph.boundaryTimerIds()) {
                boolean reaches = graph.canReachCompensation(timerId);
                timerFindings.add(new LintResult.TimerFinding(timerId, graph.displayName(timerId), reaches));
            }
        }

        List<LintResult.GatewayFinding> gatewayFindings = new ArrayList<>();
        if (hasCompensation) {
            for (String gatewayId : graph.gatewayIds()) {
                boolean anyBranchReaches = graph.outgoing(gatewayId).stream().anyMatch(graph::canReachCompensation);
                gatewayFindings.add(new LintResult.GatewayFinding(gatewayId, graph.displayName(gatewayId), anyBranchReaches));
            }
        }

        return new LintResult(bpmnFile.getName(), hasCompensation, timerFindings, gatewayFindings);
    }
}
