package com.jenish.smartparking.reservation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jenish.smartparking.allocation.domain.VehicleIdentifier;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.SizeClass;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReservationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-23T10:00:00Z");

    private static final Instant STARTS_AT = Instant.parse("2026-08-23T12:00:00Z");

    private static final Instant ENDS_AT = Instant.parse("2026-08-23T13:00:00Z");

    private final FacilityId facilityId = FacilityId.newId();

    private final VehicleIdentifier vehicleIdentifier = new VehicleIdentifier("ABC 123");

    private final Reservation confirmed = Reservation.confirm(
            ReservationId.newId(),
            facilityId,
            vehicleIdentifier,
            SizeClass.MEDIUM,
            new ReservationWindow(STARTS_AT, ENDS_AT),
            CREATED_AT);

    @Test
    void matchesAnArrivalInsideTheWindow() {
        assertTrue(confirmed.matchesArrival(facilityId, vehicleIdentifier, STARTS_AT));
        assertFalse(confirmed.matchesArrival(facilityId, vehicleIdentifier, ENDS_AT));
        assertFalse(confirmed.matchesArrival(FacilityId.newId(), vehicleIdentifier, STARTS_AT));
        assertFalse(confirmed.matchesArrival(
                facilityId,
                new VehicleIdentifier("OTHER 1"),
                STARTS_AT));
    }

    @Test
    void fulfillsAConfirmedReservationInsideItsWindow() {
        Reservation fulfilled = confirmed.fulfill(STARTS_AT.plusSeconds(60));

        assertEquals(ReservationStatus.FULFILLED, fulfilled.status());
        assertEquals(STARTS_AT.plusSeconds(60), fulfilled.resolvedAt());
        assertFalse(fulfilled.matchesArrival(facilityId, vehicleIdentifier, STARTS_AT.plusSeconds(120)));
    }

    @Test
    void rejectsFulfillmentOutsideTheArrivalWindow() {
        assertThrows(InvalidReservationStateException.class, () -> confirmed.fulfill(STARTS_AT.minusNanos(1)));
        assertThrows(InvalidReservationStateException.class, () -> confirmed.fulfill(ENDS_AT));
    }

    @Test
    void cancelsAConfirmedReservationBeforeItsWindowEnds() {
        Reservation cancelled = confirmed.cancel(STARTS_AT);

        assertEquals(ReservationStatus.CANCELLED, cancelled.status());
        assertThrows(InvalidReservationStateException.class, () -> cancelled.cancel(STARTS_AT.plusSeconds(1)));
    }

    @Test
    void expiresAConfirmedReservationWhenItsWindowEnds() {
        Reservation expired = confirmed.expire(ENDS_AT);

        assertEquals(ReservationStatus.EXPIRED, expired.status());
        assertThrows(InvalidReservationStateException.class, () -> confirmed.expire(ENDS_AT.minusNanos(1)));
    }

    @Test
    void rejectsAnInvalidWindow() {
        assertThrows(IllegalArgumentException.class, () -> new ReservationWindow(STARTS_AT, STARTS_AT));
    }

    @Test
    void rejectsCreationAfterTheArrivalWindowStarts() {
        assertThrows(IllegalArgumentException.class, () -> Reservation.confirm(
                ReservationId.newId(),
                facilityId,
                vehicleIdentifier,
                SizeClass.SMALL,
                new ReservationWindow(STARTS_AT, ENDS_AT),
                STARTS_AT.plusNanos(1)));
    }
}
