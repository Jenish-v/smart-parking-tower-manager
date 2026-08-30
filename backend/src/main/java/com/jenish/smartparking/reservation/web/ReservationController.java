package com.jenish.smartparking.reservation.web;

import com.jenish.smartparking.allocation.domain.VehicleIdentifier;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.reservation.application.ReservationNotFoundException;
import com.jenish.smartparking.reservation.application.ReservationService;
import com.jenish.smartparking.reservation.domain.Reservation;
import com.jenish.smartparking.reservation.domain.ReservationId;
import com.jenish.smartparking.reservation.domain.ReservationWindow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/facilities/{facilityId}/reservations")
@Validated
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(@Lazy ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PutMapping("/{reservationId}")
    public ResponseEntity<ReservationResponse> create(
            @PathVariable UUID facilityId,
            @PathVariable UUID reservationId,
            @Valid @RequestBody CreateReservationRequest request) {
        Reservation reservation = reservationService.create(
                new ReservationId(reservationId),
                new FacilityId(facilityId),
                new VehicleIdentifier(request.vehicleIdentifier()),
                SizeClass.valueOf(request.requiredSize()),
                new ReservationWindow(request.startsAt(), request.endsAt()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ReservationResponse.from(reservation));
    }

    @DeleteMapping("/{reservationId}")
    public ReservationResponse cancel(
            @PathVariable UUID facilityId,
            @PathVariable UUID reservationId) {
        return ReservationResponse.from(reservationService.cancel(
                new FacilityId(facilityId),
                new ReservationId(reservationId)));
    }

    @GetMapping("/{reservationId}")
    public ReservationResponse find(
            @PathVariable UUID facilityId,
            @PathVariable UUID reservationId) {
        ReservationId id = new ReservationId(reservationId);
        return reservationService.find(new FacilityId(facilityId), id)
                .map(ReservationResponse::from)
                .orElseThrow(() -> new ReservationNotFoundException(id));
    }

    @GetMapping
    public List<ReservationResponse> history(
            @PathVariable UUID facilityId,
            @RequestParam
            @NotBlank
            @Size(max = 32)
            String vehicleIdentifier) {
        return reservationService.history(
                        new FacilityId(facilityId),
                        new VehicleIdentifier(vehicleIdentifier))
                .stream()
                .map(ReservationResponse::from)
                .toList();
    }
}
