package com.jenish.smartparking.parkingsession.web;

import com.jenish.smartparking.allocation.domain.VehicleIdentifier;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.parkingsession.application.NoActiveParkingSessionException;
import com.jenish.smartparking.parkingsession.application.ParkingSessionService;
import com.jenish.smartparking.parkingsession.domain.RequestId;
import com.jenish.smartparking.parkingsession.domain.ParkingSession;
import com.jenish.smartparking.pricing.application.PricingService;
import com.jenish.smartparking.pricing.domain.ParkingReceipt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/facilities/{facilityId}/parking-sessions")
@Validated
public class ParkingSessionController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final ParkingSessionService sessionService;

    private final PricingService pricingService;

    public ParkingSessionController(
            @Lazy ParkingSessionService sessionService,
            @Lazy PricingService pricingService) {
        this.sessionService = sessionService;
        this.pricingService = pricingService;
    }

    @PostMapping("/entries")
    public ResponseEntity<ParkingSessionResponse> enter(
            @PathVariable UUID facilityId,
            @RequestHeader(IDEMPOTENCY_KEY) UUID requestId,
            @Valid @RequestBody EntryParkingSessionRequest request) {
        ParkingSessionResponse response = ParkingSessionResponse.from(sessionService.enter(
                new RequestId(requestId),
                new FacilityId(facilityId),
                new VehicleIdentifier(request.vehicleIdentifier()),
                SizeClass.valueOf(request.requiredSize())));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/exits")
    public ParkingSessionResponse exit(
            @PathVariable UUID facilityId,
            @RequestHeader(IDEMPOTENCY_KEY) UUID requestId,
            @Valid @RequestBody ExitParkingSessionRequest request) {
        return ParkingSessionResponse.from(sessionService.exit(
                new RequestId(requestId),
                new FacilityId(facilityId),
                new VehicleIdentifier(request.vehicleIdentifier())));
    }

    @GetMapping("/active")
    public ParkingSessionResponse findActive(
            @PathVariable UUID facilityId,
            @RequestParam
            @NotBlank
            @Size(max = 32)
            String vehicleIdentifier) {
        VehicleIdentifier vehicle = new VehicleIdentifier(vehicleIdentifier);
        return sessionService.findActive(new FacilityId(facilityId), vehicle)
                .map(ParkingSessionResponse::from)
                .orElseThrow(() -> new NoActiveParkingSessionException(vehicle));
    }

    @GetMapping
    public List<ParkingSessionResponse> history(
            @PathVariable UUID facilityId,
            @RequestParam
            @NotBlank
            @Size(max = 32)
            String vehicleIdentifier) {
        List<ParkingSession> sessions = sessionService.history(
                new FacilityId(facilityId),
                new VehicleIdentifier(vehicleIdentifier));
        Map<UUID, ParkingReceipt> receipts = pricingService.findReceipts(
                sessions.stream().map(session -> session.id().value()).toList());
        return sessions
                .stream()
                .map(session -> {
                    ParkingReceipt receipt = receipts.get(session.id().value());
                    return receipt == null
                            ? ParkingSessionResponse.from(session)
                            : ParkingSessionResponse.from(session, receipt);
                })
                .toList();
    }
}
