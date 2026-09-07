package com.jenish.smartparking.audit.web;

import com.jenish.smartparking.audit.application.AuditHistory;
import com.jenish.smartparking.facility.domain.FacilityId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/facilities/{facilityId}/audit-events")
public class AuditController {

    private final AuditHistory auditHistory;

    public AuditController(@Lazy AuditHistory auditHistory) {
        this.auditHistory = auditHistory;
    }

    @GetMapping
    public List<AuditEventResponse> find(
            @PathVariable UUID facilityId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant before,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        Instant beforeExclusive = before == null ? Instant.now() : before;
        return auditHistory.find(new FacilityId(facilityId), beforeExclusive, limit).stream()
                .map(AuditEventResponse::from)
                .toList();
    }
}
