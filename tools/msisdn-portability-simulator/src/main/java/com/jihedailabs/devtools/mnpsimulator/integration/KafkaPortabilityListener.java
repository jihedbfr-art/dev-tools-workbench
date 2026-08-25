package com.jihedailabs.devtools.mnpsimulator.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jihedailabs.devtools.mnpsimulator.service.MnpLookupService;
import com.jihedailabs.devtools.mnpsimulator.service.MnpScenarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(name = "mnp-simulator.integration.enabled", havingValue = "true")
@Slf4j
public class KafkaPortabilityListener {

    private final ObjectMapper objectMapper;
    private final MnpLookupService lookupService;
    private final RestTemplate restTemplate;
    private final String donorResponseBaseUrl;

    public KafkaPortabilityListener(
            ObjectMapper objectMapper, 
            MnpLookupService lookupService, 
            RestTemplateBuilder restTemplateBuilder,
            @Value("${mnp-simulator.integration.donor-response-base-url:http://localhost:8080}") String donorResponseBaseUrl) {
        this.objectMapper = objectMapper;
        this.lookupService = lookupService;
        this.restTemplate = restTemplateBuilder.build();
        this.donorResponseBaseUrl = donorResponseBaseUrl;
    }

    @KafkaListener(topics = "${mnp-simulator.integration.notification-topic:number-portability-events}", groupId = "mnp-simulator")
    public void onEvent(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            if (!"donor.notification.requested".equals(root.path("eventType").asText())) {
                return;
            }

            String requestId = root.path("requestId").asText();
            JsonNode payload = root.path("payload");
            String msisdn = payload.path("msisdn").asText();
            String recipientOperator = payload.path("recipientOperator").asText();

            log.info("Received donor notification request {} for {}", requestId, msisdn);

            // Execute asynchronously so we don't block the Kafka consumer thread with simulated latencies
            CompletableFuture.runAsync(() -> processPortRequest(requestId, msisdn, recipientOperator));

        } catch (Exception e) {
            log.error("Failed to process Kafka event", e);
        }
    }

    private void processPortRequest(String requestId, String msisdn, String recipientOperator) {
        try {
            MnpLookupService.PortResult result = lookupService.requestPort(msisdn, recipientOperator);
            
            if (result.decision() == MnpScenarioService.Decision.TIMEOUT) {
                log.warn("Simulating TIMEOUT for request {} - no response will be sent", requestId);
                return;
            }
            
            String url = donorResponseBaseUrl + "/api/portability/" + requestId + "/donor-response";
            String decisionBody = "{\"decision\": \"" + result.decision().name() + "\"}";
            
            log.info("Sending donor response {} to {}", result.decision(), url);
            
            // In a real scenario we'd use a typed DTO, but for the simulator a raw string is fine
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<String> req = new org.springframework.http.HttpEntity<>(decisionBody, headers);
            
            restTemplate.postForEntity(url, req, String.class);
            
        } catch (Exception e) {
            log.error("Error processing port request {} asynchronously", requestId, e);
        }
    }
}
