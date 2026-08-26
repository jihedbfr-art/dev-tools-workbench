package com.jihedailabs.devtools.sagalinter;

import java.util.List;

public record LintResult(
        String fileName,
        boolean hasCompensationNode,
        List<TimerFinding> timerFindings,
        List<GatewayFinding> gatewayFindings
) {
    public record TimerFinding(String timerId, String timerName, boolean reachesCompensation) {
        public boolean isViolation() {
            return !reachesCompensation;
        }
    }

    /** Informational only — this linter can't parse condition expressions, so it can't know
     * which branch of a gateway is "the failure path." It can only observe whether ANY branch
     * reaches compensation, which is a weaker but still useful signal: a gateway added later
     * whose rejecting branch forgot to route to cleanup would show none. */
    public record GatewayFinding(String gatewayId, String gatewayName, boolean anyBranchReachesCompensation) {
    }

    public boolean hasHardFailure() {
        return timerFindings.stream().anyMatch(TimerFinding::isViolation);
    }
}
