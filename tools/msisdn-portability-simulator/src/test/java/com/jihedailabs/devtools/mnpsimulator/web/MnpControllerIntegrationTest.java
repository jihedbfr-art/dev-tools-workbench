package com.jihedailabs.devtools.mnpsimulator.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "mnp-simulator.scenario.reject-rate=0.0",
        "mnp-simulator.scenario.timeout-rate=0.0",
        "mnp-simulator.scenario.min-latency-ms=0",
        "mnp-simulator.scenario.max-latency-ms=0",
        "mnp-simulator.integration.enabled=false"
})
class MnpControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testLookupKnownNumber() throws Exception {
        mockMvc.perform(get("/api/mnp/lookup/+21698000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msisdn").value("+21698000000"))
                .andExpect(jsonPath("$.portedStatus").value(false))
                .andExpect(jsonPath("$.donorNetwork").value("TT"))
                .andExpect(jsonPath("$.recipientNetwork").value("TT"));
    }

    @Test
    void testLookupPortedNumber() throws Exception {
        mockMvc.perform(get("/api/mnp/lookup/+21622999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msisdn").value("+21622999999"))
                .andExpect(jsonPath("$.portedStatus").value(true))
                .andExpect(jsonPath("$.donorNetwork").value("OOR"))
                .andExpect(jsonPath("$.recipientNetwork").value("TT"));
    }

    @Test
    void testLookupUnknownNumber() throws Exception {
        mockMvc.perform(get("/api/mnp/lookup/+21600000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testPortSuccess() throws Exception {
        String request = """
                {
                    "msisdn": "+21650000000",
                    "recipientOperatorCode": "OOR"
                }
                """;

        mockMvc.perform(post("/api/mnp/port")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ACCEPTED"))
                .andExpect(jsonPath("$.donorOperatorCode").value("ORA"))
                .andExpect(jsonPath("$.recipientOperatorCode").value("OOR"));

        // Verify it was updated
        mockMvc.perform(get("/api/mnp/lookup/+21650000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portedStatus").value(true))
                .andExpect(jsonPath("$.donorNetwork").value("ORA"))
                .andExpect(jsonPath("$.recipientNetwork").value("OOR"));
    }

    @Test
    void testSecondPortReportsCurrentHolderAsDonorNotOriginalRangeHolder() throws Exception {
        // +21622999999 was already ported once: OOR (range holder) -> TT (current holder).
        // A second port away from TT must report TT as the donor for this transaction, not OOR.
        String request = """
                {
                    "msisdn": "+21622999999",
                    "recipientOperatorCode": "ORA"
                }
                """;

        mockMvc.perform(post("/api/mnp/port")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ACCEPTED"))
                .andExpect(jsonPath("$.donorOperatorCode").value("TT"))
                .andExpect(jsonPath("$.recipientOperatorCode").value("ORA"));
    }

    @Test
    void testPortAlreadyOnRecipient() throws Exception {
        String request = """
                {
                    "msisdn": "+21622000000",
                    "recipientOperatorCode": "OOR"
                }
                """;

        mockMvc.perform(post("/api/mnp/port")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict());
    }
}
