package com.jenish.smartparking.facility.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FacilityTest {

    @Test
    void ordersTheStructureAndReportsCapacity() {
        ParkingFloor second = floor(2, "B", 2);
        ParkingFloor first = floor(1, "A", 3);

        Facility facility = new Facility(id(), "  Central Tower  ", List.of(second, first));

        assertEquals("Central Tower", facility.name());
        assertEquals(List.of(new FloorNumber(1), new FloorNumber(2)),
                facility.floors().stream().map(ParkingFloor::number).toList());
        assertEquals(5, facility.capacity());
    }

    @Test
    void rejectsDuplicateFloorNumbers() {
        assertThrows(IllegalArgumentException.class,
                () -> new Facility(id(), "Central Tower", List.of(floor(1, "A", 1), floor(1, "B", 1))));
    }

    @Test
    void rejectsDuplicateZoneCodesAndSpaceNumbers() {
        ParkingZone zone = zone("A", 1);
        assertThrows(IllegalArgumentException.class,
                () -> new ParkingFloor(new FloorNumber(1), List.of(zone, zone)));

        ParkingSpace space = activeSpace(1, SizeClass.SMALL);
        assertThrows(IllegalArgumentException.class,
                () -> new ParkingZone(new ZoneCode("A"), List.of(space, space)));
    }

    private static FacilityId id() {
        return new FacilityId(UUID.fromString("d936bb7d-3027-47aa-a47b-d04a37e07310"));
    }

    private static ParkingFloor floor(int floorNumber, String zoneCode, int capacity) {
        return new ParkingFloor(new FloorNumber(floorNumber), List.of(zone(zoneCode, capacity)));
    }

    private static ParkingZone zone(String code, int capacity) {
        List<ParkingSpace> spaces = java.util.stream.IntStream.rangeClosed(1, capacity)
                .mapToObj(number -> activeSpace(number, SizeClass.SMALL))
                .toList();
        return new ParkingZone(new ZoneCode(code), spaces);
    }

    private static ParkingSpace activeSpace(int number, SizeClass sizeClass) {
        return new ParkingSpace(new SpaceNumber(number), sizeClass, SpaceOperationalState.ACTIVE);
    }
}
