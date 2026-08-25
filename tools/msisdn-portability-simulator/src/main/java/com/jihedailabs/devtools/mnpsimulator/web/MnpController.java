package com.jihedailabs.devtools.mnpsimulator.web;

import com.jihedailabs.devtools.mnpsimulator.domain.PortedNumber;
import com.jihedailabs.devtools.mnpsimulator.service.MnpLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mnp")
@RequiredArgsConstructor
public class MnpController {

    private final MnpLookupService lookupService;

    @GetMapping("/lookup/{msisdn}")
    public LookupResponse lookup(@PathVariable String msisdn) {
        PortedNumber number = lookupService.lookup(msisdn);
        return new LookupResponse(
                number.getMsisdn(),
                number.isPorted(),
                number.getDonorOperator().getCode(),
                number.getCurrentOperator().getCode(),
                number.getCurrentOperator().getRoutingPrefix()
        );
    }

    @PostMapping("/port")
    public MnpLookupService.PortResult port(@RequestBody PortRequest request) {
        return lookupService.requestPort(request.msisdn(), request.recipientOperatorCode());
    }

    public record LookupResponse(
            String msisdn,
            boolean portedStatus,
            String donorNetwork,
            String recipientNetwork,
            String routingNumber
    ) {}

    public record PortRequest(
            String msisdn,
            String recipientOperatorCode
    ) {}
}
