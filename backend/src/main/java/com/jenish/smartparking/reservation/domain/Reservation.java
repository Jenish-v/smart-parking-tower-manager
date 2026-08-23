package com.jenish.smartparking.reservation.domain;

import com.jenish.smartparking.allocation.domain.VehicleIdentifier;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.SizeClass;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public record Reservation(
        ReservationId id,
        FacilityId facilityId,
        VehicleIdentifier vehicleIdentifier,
        SizeClass requiredSize,
        ReservationWindow window,
        Instant createdAt,
        ReservationStatus status,
        Instant resolvedAt) {

    public Reservation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(facilityId, "facilityId must not be null");
        Objects.requireNonNull(vehicleIdentifier, "vehicleIdentifier must not be null");
        Objects.requireNonNull(requiredSize, "requiredSize must not be null");
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (createdAt.isAfter(window.startsAt())) {
            throw new IllegalArgumentException("reservation must be created no later than its start");
        }
        validateResolution(status, resolvedAt, createdAt, window);
    }

    public static Reservation confirm(
            ReservationId id,
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            SizeClass requiredSize,
            ReservationWindow window,
            Instant createdAt) {
        return new Reservation(
                id,
                facilityId,
                vehicleIdentifier,
                requiredSize,
                window,
                createdAt,
                ReservationStatus.CONFIRMED,
                null);
    }

    public boolean matchesArrival(
            FacilityId arrivalFacilityId,
            VehicleIdentifier arrivalVehicleIdentifier,
            Instant arrivedAt) {
        return status == ReservationStatus.CONFIRMED
                && facilityId.equals(arrivalFacilityId)
                && vehicleIdentifier.equals(arrivalVehicleIdentifier)
                && window.contains(arrivedAt);
    }

    public Reservation cancel(Instant cancelledAt) {
        Objects.requireNonNull(cancelledAt, "cancelledAt must not be null");
        requireConfirmed();
        if (cancelledAt.isBefore(createdAt) || !cancelledAt.isBefore(window.endsAt())) {
            throw invalid("cannot be cancelled at the requested time");
        }
        return resolve(ReservationStatus.CANCELLED, cancelledAt);
    }

    public Reservation fulfill(Instant fulfilledAt) {
        Objects.requireNonNull(fulfilledAt, "fulfilledAt must not be null");
        requireConfirmed();
        if (!window.contains(fulfilledAt)) {
            throw invalid("cannot be fulfilled outside its arrival window");
        }
        return resolve(ReservationStatus.FULFILLED, fulfilledAt);
    }

    public Reservation expire(Instant expiredAt) {
        Objects.requireNonNull(expiredAt, "expiredAt must not be null");
        requireConfirmed();
        if (expiredAt.isBefore(window.endsAt())) {
            throw invalid("cannot expire before its arrival window ends");
        }
        return resolve(ReservationStatus.EXPIRED, expiredAt);
    }

    private Reservation resolve(ReservationStatus resolvedStatus, Instant resolvedTime) {
        Objects.requireNonNull(resolvedTime, "resolved time must not be null");
        return new Reservation(
                id,
                facilityId,
                vehicleIdentifier,
                requiredSize,
                window,
                createdAt,
                resolvedStatus,
                resolvedTime);
    }

    private void requireConfirmed() {
        if (status != ReservationStatus.CONFIRMED) {
            throw invalid("is already " + status.name().toLowerCase(Locale.ROOT));
        }
    }

    private InvalidReservationStateException invalid(String reason) {
        return new InvalidReservationStateException(id, reason);
    }

    private static void validateResolution(
            ReservationStatus status,
            Instant resolvedAt,
            Instant createdAt,
            ReservationWindow window) {
        if (status == ReservationStatus.CONFIRMED) {
            if (resolvedAt != null) {
                throw new IllegalArgumentException("confirmed reservation must not have a resolution time");
            }
            return;
        }
        Objects.requireNonNull(resolvedAt, "resolvedAt must not be null for a terminal reservation");
        if (resolvedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("reservation cannot resolve before it is created");
        }
        if (status == ReservationStatus.CANCELLED && !resolvedAt.isBefore(window.endsAt())) {
            throw new IllegalArgumentException("cancelled reservation must resolve before its window ends");
        }
        if (status == ReservationStatus.FULFILLED && !window.contains(resolvedAt)) {
            throw new IllegalArgumentException("fulfilled reservation must resolve inside its window");
        }
        if (status == ReservationStatus.EXPIRED && resolvedAt.isBefore(window.endsAt())) {
            throw new IllegalArgumentException("expired reservation must resolve when its window ends or later");
        }
    }
}
