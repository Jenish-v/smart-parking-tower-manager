package com.jenish.smartparking.allocation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jenish.smartparking.facility.domain.Facility;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.FloorNumber;
import com.jenish.smartparking.facility.domain.ParkingFloor;
import com.jenish.smartparking.facility.domain.ParkingSpace;
import com.jenish.smartparking.facility.domain.ParkingZone;
import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.facility.domain.SpaceNumber;
import com.jenish.smartparking.facility.domain.SpaceOperationalState;
import com.jenish.smartparking.facility.domain.ZoneCode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AllocationManagerTest {

    @Test
    void allocatesByFloorThenZoneThenSpace() {
        AllocationManager manager = new AllocationManager(facility(
                floor(2, zone("A", active(1, SizeClass.LARGE))),
                floor(1,
                        zone("B", active(1, SizeClass.LARGE)),
                        zone("A", active(2, SizeClass.MEDIUM), active(1, SizeClass.SMALL)))));

        ParkingAllocation first = manager.park(vehicle("car-1"), SizeClass.SMALL);
        ParkingAllocation second = manager.park(vehicle("car-2"), SizeClass.SMALL);

        assertEquals(location(1, "A", 1), first.spaceLocation());
        assertEquals(location(1, "A", 2), second.spaceLocation());
    }

    @Test
    void skipsIncompatibleAndOutOfServiceSpaces() {
        AllocationManager manager = new AllocationManager(facility(floor(1,
                zone("A",
                        active(1, SizeClass.SMALL),
                        outOfService(2, SizeClass.LARGE),
                        active(3, SizeClass.LARGE)))));

        ParkingAllocation allocation = manager.park(vehicle("truck-1"), SizeClass.LARGE);

        assertEquals(location(1, "A", 3), allocation.spaceLocation());
    }

    @Test
    void enforcesVehicleAndSpaceUniqueness() {
        AllocationManager manager = new AllocationManager(facility(floor(1,
                zone("A", active(1, SizeClass.SMALL)))));
        VehicleIdentifier firstVehicle = vehicle("car-1");
        manager.park(firstVehicle, SizeClass.SMALL);

        assertThrows(VehicleAlreadyParkedException.class,
                () -> manager.park(firstVehicle, SizeClass.SMALL));
        assertThrows(ParkingCapacityExceededException.class,
                () -> manager.park(vehicle("car-2"), SizeClass.SMALL));
    }

    @Test
    void findsAndUnparksAnAllocation() {
        AllocationManager manager = new AllocationManager(facility(floor(1,
                zone("A", active(1, SizeClass.SMALL)))));
        VehicleIdentifier vehicle = vehicle("car-1");
        ParkingAllocation parked = manager.park(vehicle, SizeClass.SMALL);

        assertEquals(parked, manager.find(vehicle).orElseThrow());
        assertEquals(parked, manager.unpark(vehicle));
        assertFalse(manager.find(vehicle).isPresent());
        assertThrows(VehicleNotParkedException.class, () -> manager.unpark(vehicle));

        ParkingAllocation replacement = manager.park(vehicle("car-2"), SizeClass.SMALL);
        assertEquals(parked.spaceLocation(), replacement.spaceLocation());
    }

    @Test
    void reportsPhysicalAndCompatibleAvailability() {
        AllocationManager manager = new AllocationManager(facility(floor(1,
                zone("A",
                        active(1, SizeClass.SMALL),
                        active(2, SizeClass.MEDIUM),
                        active(3, SizeClass.LARGE),
                        outOfService(4, SizeClass.LARGE)))));
        manager.park(vehicle("car-1"), SizeClass.SMALL);

        AvailabilitySnapshot snapshot = manager.availability();

        assertEquals(3, snapshot.operationalSpaces());
        assertEquals(1, snapshot.occupiedSpaces());
        assertEquals(2, snapshot.availableSpaces());
        assertEquals(0L, snapshot.availableByPhysicalSize().get(SizeClass.SMALL));
        assertEquals(1L, snapshot.availableByPhysicalSize().get(SizeClass.MEDIUM));
        assertEquals(1L, snapshot.availableByPhysicalSize().get(SizeClass.LARGE));
        assertEquals(2, snapshot.availableFor(SizeClass.SMALL));
        assertEquals(2, snapshot.availableFor(SizeClass.MEDIUM));
        assertEquals(1, snapshot.availableFor(SizeClass.LARGE));
    }

    @Test
    void normalizesVehicleIdentifiers() {
        assertEquals("ON 123-AB", vehicle("  on   123-ab ").value());
        assertThrows(IllegalArgumentException.class, () -> vehicle("ON/123"));
    }

    private static Facility facility(ParkingFloor... floors) {
        return new Facility(
                new FacilityId(UUID.fromString("d936bb7d-3027-47aa-a47b-d04a37e07310")),
                "Test Tower",
                List.of(floors));
    }

    private static ParkingFloor floor(int number, ParkingZone... zones) {
        return new ParkingFloor(new FloorNumber(number), List.of(zones));
    }

    private static ParkingZone zone(String code, ParkingSpace... spaces) {
        return new ParkingZone(new ZoneCode(code), List.of(spaces));
    }

    private static ParkingSpace active(int number, SizeClass sizeClass) {
        return new ParkingSpace(new SpaceNumber(number), sizeClass, SpaceOperationalState.ACTIVE);
    }

    private static ParkingSpace outOfService(int number, SizeClass sizeClass) {
        return new ParkingSpace(new SpaceNumber(number), sizeClass, SpaceOperationalState.OUT_OF_SERVICE);
    }

    private static VehicleIdentifier vehicle(String value) {
        return new VehicleIdentifier(value);
    }

    private static SpaceLocation location(int floor, String zone, int space) {
        return new SpaceLocation(
                new FacilityId(UUID.fromString("d936bb7d-3027-47aa-a47b-d04a37e07310")),
                new FloorNumber(floor),
                new ZoneCode(zone),
                new SpaceNumber(space));
    }
}
