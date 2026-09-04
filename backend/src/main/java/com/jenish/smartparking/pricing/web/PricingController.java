package com.jenish.smartparking.pricing.web;

import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.pricing.application.PricingService;
import com.jenish.smartparking.pricing.domain.AdjustmentReason;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/facilities/{facilityId}/parking-sessions/{sessionId}/receipt")
public final class PricingController {

    private final PricingService pricingService;

    public PricingController(@Lazy PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping
    public ReceiptStatementResponse findReceipt(
            @PathVariable UUID facilityId,
            @PathVariable UUID sessionId) {
        return ReceiptStatementResponse.from(pricingService.findStatement(
                new FacilityId(facilityId),
                sessionId));
    }

    @PutMapping("/adjustments/{adjustmentId}")
    public ReceiptStatementResponse adjust(
            @PathVariable UUID facilityId,
            @PathVariable UUID sessionId,
            @PathVariable UUID adjustmentId,
            @Valid @RequestBody AdjustFeeRequest request) {
        return ReceiptStatementResponse.from(pricingService.adjust(
                new FacilityId(facilityId),
                sessionId,
                adjustmentId,
                request.amountMinor(),
                AdjustmentReason.valueOf(request.reason()),
                request.reasonDetail(),
                request.operatorReference()));
    }
}
