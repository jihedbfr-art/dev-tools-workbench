package com.jihedailabs.devtools.mnpsimulator.service;

import com.jihedailabs.devtools.mnpsimulator.config.MnpSimulatorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class MnpScenarioService {

    private final MnpSimulatorProperties properties;
    private final Random random;

    public enum Decision {
        ACCEPTED, REJECTED, TIMEOUT
    }

    public Decision evaluatePortRequest(String recipientOperatorCode) {
        double currentRejectRate = properties.rejectRate();
        double currentTimeoutRate = properties.timeoutRate();

        if (properties.operatorOverrides().containsKey(recipientOperatorCode)) {
            MnpSimulatorProperties.OperatorOverride override = properties.operatorOverrides().get(recipientOperatorCode);
            if (override.rejectRate() != null) {
                currentRejectRate = override.rejectRate();
            }
            if (override.timeoutRate() != null) {
                currentTimeoutRate = override.timeoutRate();
            }
        }

        double roll = random.nextDouble();
        Decision decision;
        
        if (roll < currentTimeoutRate) {
            decision = Decision.TIMEOUT;
        } else if (roll < currentTimeoutRate + currentRejectRate) {
            decision = Decision.REJECTED;
        } else {
            decision = Decision.ACCEPTED;
        }
        
        log.info("Evaluated port request to {} -> {}", recipientOperatorCode, decision);
        return decision;
    }

    public void applySimulatedLatency(Decision decision) {
        int delay;
        if (decision == Decision.TIMEOUT) {
            delay = properties.timeoutDelayMs();
        } else {
            int min = properties.minLatencyMs();
            int max = Math.max(min, properties.maxLatencyMs());
            delay = min + random.nextInt((max - min) + 1);
        }

        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
