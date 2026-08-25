package com.jihedailabs.devtools.mnpsimulator.service;

import com.jihedailabs.devtools.mnpsimulator.domain.Operator;
import com.jihedailabs.devtools.mnpsimulator.domain.PortedNumber;
import com.jihedailabs.devtools.mnpsimulator.repository.OperatorRepository;
import com.jihedailabs.devtools.mnpsimulator.repository.PortedNumberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MnpLookupService {

    private final PortedNumberRepository portedNumberRepository;
    private final OperatorRepository operatorRepository;
    private final MnpScenarioService scenarioService;

    @Transactional(readOnly = true)
    public PortedNumber lookup(String msisdn) {
        return portedNumberRepository.findById(msisdn)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MSISDN not found: " + msisdn));
    }

    @Transactional
    public PortResult requestPort(String msisdn, String recipientOperatorCode) {
        PortedNumber number = lookup(msisdn);
        
        Operator recipient = operatorRepository.findById(recipientOperatorCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown recipient operator: " + recipientOperatorCode));

        if (number.getCurrentOperator().getCode().equals(recipientOperatorCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "MSISDN is already on operator " + recipientOperatorCode);
        }

        // The donor for THIS transaction is whoever currently holds the number, not the
        // permanent range holder — capture it before a possible update below. A number ported
        // more than once must report its most recent holder as the donor on its second request,
        // not the original range holder forever.
        String donorOperatorCodeForThisTransaction = number.getCurrentOperator().getCode();

        MnpScenarioService.Decision decision = scenarioService.evaluatePortRequest(recipientOperatorCode);
        scenarioService.applySimulatedLatency(decision);

        if (decision == MnpScenarioService.Decision.ACCEPTED) {
            number.setCurrentOperator(recipient);
            number.setLastPortedAt(Instant.now());
            portedNumberRepository.save(number);
        }

        return new PortResult(msisdn, donorOperatorCodeForThisTransaction, recipientOperatorCode, decision);
    }

    public record PortResult(String msisdn, String donorOperatorCode, String recipientOperatorCode, MnpScenarioService.Decision decision) {}
}
