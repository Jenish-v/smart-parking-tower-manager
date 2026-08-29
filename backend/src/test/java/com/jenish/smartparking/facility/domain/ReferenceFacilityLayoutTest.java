package com.jenish.smartparking.facility.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ReferenceFacilityLayoutTest {

    @Test
    void recognizesTheSixBySixByTwoHundredReferenceLayout() {
        Facility facility = referenceFacility();

        assertEquals(7_200, facility.capacity());
        assertTrue(ReferenceFacilityLayout.matches(facility));
    }

    @Test
    void rejectsAFacilityWithAnIncompleteZone() {
        Facility facility = new Facility(id(), "Incomplete", List.of(floor(1, 199)));

        assertFalse(ReferenceFacilityLayout.matches(facility));
    }

    private static Facility referenceFacility() {
        List<ParkingFloor> floors = IntStream.rangeClosed(1, ReferenceFacilityLayout.FLOOR_COUNT)
                .mapToObj(number -> floor(number, ReferenceFacilityLayout.SPACE_COUNT_PER_ZONE))
                .toList();
        return new Facility(id(), "Reference Tower", floors);
    }

    private static ParkingFloor floor(int number, int spacesPerZone) {
        List<ParkingZone> zones = ReferenceFacilityLayout.ZONE_CODES.stream()
                .map(code -> zone(code, spacesPerZone))
                .toList();
        return new ParkingFloor(new FloorNumber(number), zones);
    }

    private static ParkingZone zone(ZoneCode code, int capacity) {
        List<ParkingSpace> spaces = IntStream.rangeClosed(1, capacity)
                .mapToObj(number -> new ParkingSpace(
                        new SpaceNumber(number), SizeClass.MEDIUM, SpaceOperationalState.ACTIVE))
                .toList();
        return new ParkingZone(code, spaces);
    }

    private static FacilityId id() {
        return new FacilityId(UUID.fromString("d936bb7d-3027-47aa-a47b-d04a37e07310"));
    }
}
