package com.jihedailabs.devtools.mnpsimulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "mnp-simulator.scenario")
public record MnpSimulatorProperties(
    double rejectRate,
    double timeoutRate,
    int minLatencyMs,
    int maxLatencyMs,
    int timeoutDelayMs,
    Map<String, OperatorOverride> operatorOverrides
) {
    public MnpSimulatorProperties {
        if (operatorOverrides == null) {
            operatorOverrides = new HashMap<>();
        }
    }

    public record OperatorOverride(
        Double rejectRate,
        Double timeoutRate
    ) {}
}
