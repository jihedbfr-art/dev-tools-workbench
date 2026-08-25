package com.jihedailabs.devtools.mnpsimulator.service;

import com.jihedailabs.devtools.mnpsimulator.config.MnpSimulatorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class MnpScenarioServiceTest {

    private Random randomMock;

    @BeforeEach
    void setUp() {
        randomMock = Mockito.mock(Random.class);
    }

    @Test
    void testEvaluatePortRequest_Timeout() {
        MnpSimulatorProperties props = new MnpSimulatorProperties(0.1, 0.2, 50, 100, 3000, Map.of());
        MnpScenarioService service = new MnpScenarioService(props, randomMock);

        // Timeout rate is 0.2, so a roll of 0.15 is < 0.2
        when(randomMock.nextDouble()).thenReturn(0.15);
        assertEquals(MnpScenarioService.Decision.TIMEOUT, service.evaluatePortRequest("ORA"));
    }

    @Test
    void testEvaluatePortRequest_Rejected() {
        MnpSimulatorProperties props = new MnpSimulatorProperties(0.3, 0.2, 50, 100, 3000, Map.of());
        MnpScenarioService service = new MnpScenarioService(props, randomMock);

        // Timeout is 0.2, Reject is 0.3. Reject threshold is < (0.2+0.3) = 0.5
        when(randomMock.nextDouble()).thenReturn(0.4);
        assertEquals(MnpScenarioService.Decision.REJECTED, service.evaluatePortRequest("ORA"));
    }

    @Test
    void testEvaluatePortRequest_Accepted() {
        MnpSimulatorProperties props = new MnpSimulatorProperties(0.3, 0.2, 50, 100, 3000, Map.of());
        MnpScenarioService service = new MnpScenarioService(props, randomMock);

        // Threshold is 0.5, so 0.6 is ACCEPTED
        when(randomMock.nextDouble()).thenReturn(0.6);
        assertEquals(MnpScenarioService.Decision.ACCEPTED, service.evaluatePortRequest("ORA"));
    }

    @Test
    void testEvaluatePortRequest_WithOverride() {
        MnpSimulatorProperties props = new MnpSimulatorProperties(
                0.5, 0.5, 50, 100, 3000,
                Map.of("OOR", new MnpSimulatorProperties.OperatorOverride(0.0, 0.0))
        );
        MnpScenarioService service = new MnpScenarioService(props, randomMock);

        // Normally 0.1 would be a timeout, but OOR is overridden to 0.0/0.0
        when(randomMock.nextDouble()).thenReturn(0.1);
        assertEquals(MnpScenarioService.Decision.ACCEPTED, service.evaluatePortRequest("OOR"));
        
        // But for another operator, it still applies global rate
        assertEquals(MnpScenarioService.Decision.TIMEOUT, service.evaluatePortRequest("ORA"));
    }
}
