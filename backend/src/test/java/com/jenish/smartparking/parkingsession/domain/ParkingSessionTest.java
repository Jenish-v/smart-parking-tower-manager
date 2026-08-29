package com.jenish.smartparking.parkingsession.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jenish.smartparking.allocation.domain.SpaceLocation;
import com.jenish.smartparking.allocation.domain.VehicleIdentifier;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.FloorNumber;
import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.facility.domain.SpaceNumber;
import com.jenish.smartparking.facility.domain.ZoneCode;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ParkingSessionTest {

    private final FacilityId facilityId = FacilityId.newId();

    private final ParkingSession active = ParkingSession.start(
            SessionId.newId(),
            facilityId,
            new VehicleIdentifier("ABC 123"),
            SizeClass.MEDIUM,
            new SpaceLocation(
                    facilityId,
                    new FloorNumber(1),
                    new ZoneCode("A"),
                    new SpaceNumber(101)),
            Instant.parse("2026-08-15T12:00:00Z"));

    @Test
    void completesAnActiveSession() {
        ParkingSession completed = active.complete(Instant.parse("2026-08-15T13:00:00Z"));

        assertEquals(ParkingSessionStatus.COMPLETED, completed.status());
        assertEquals(Instant.parse("2026-08-15T13:00:00Z"), completed.exitedAt());
    }

    @Test
    void rejectsRepeatedCompletion() {
        ParkingSession completed = active.complete(Instant.parse("2026-08-15T13:00:00Z"));

        assertThrows(
                InvalidSessionStateException.class,
                () -> completed.complete(Instant.parse("2026-08-15T14:00:00Z")));
    }

    @Test
    void rejectsCompletionBeforeEntry() {
        assertThrows(
                InvalidSessionStateException.class,
                () -> active.complete(Instant.parse("2026-08-15T11:59:59Z")));
    }

    @Test
    void rejectsLocationFromAnotherFacility() {
        FacilityId otherFacility = FacilityId.newId();

        assertThrows(IllegalArgumentException.class, () -> ParkingSession.start(
                SessionId.newId(),
                facilityId,
                new VehicleIdentifier("ABC 123"),
                SizeClass.SMALL,
                new SpaceLocation(
                        otherFacility,
                        new FloorNumber(1),
                        new ZoneCode("A"),
                        new SpaceNumber(1)),
                Instant.parse("2026-08-15T12:00:00Z")));
    }
}
