package com.jenish.smartparking.facility.domain;

import java.util.List;
import java.util.stream.IntStream;

public final class ReferenceFacilityLayout {

    public static final int FLOOR_COUNT = 6;

    public static final int ZONE_COUNT_PER_FLOOR = 6;

    public static final int SPACE_COUNT_PER_ZONE = 200;

    public static final int TOTAL_SPACE_COUNT = FLOOR_COUNT * ZONE_COUNT_PER_FLOOR * SPACE_COUNT_PER_ZONE;

    public static final List<ZoneCode> ZONE_CODES = List.of(
            new ZoneCode("A"),
            new ZoneCode("B"),
            new ZoneCode("C"),
            new ZoneCode("D"),
            new ZoneCode("E"),
            new ZoneCode("F"));

    private ReferenceFacilityLayout() {
    }

    public static boolean matches(Facility facility) {
        if (facility.floors().size() != FLOOR_COUNT || facility.capacity() != TOTAL_SPACE_COUNT) {
            return false;
        }
        return IntStream.rangeClosed(1, FLOOR_COUNT)
                .allMatch(floorNumber -> matchesFloor(facility.floors().get(floorNumber - 1), floorNumber));
    }

    private static boolean matchesFloor(ParkingFloor floor, int expectedNumber) {
        if (floor.number().value() != expectedNumber || floor.zones().size() != ZONE_COUNT_PER_FLOOR) {
            return false;
        }
        return IntStream.range(0, ZONE_COUNT_PER_FLOOR)
                .allMatch(index -> matchesZone(floor.zones().get(index), ZONE_CODES.get(index)));
    }

    private static boolean matchesZone(ParkingZone zone, ZoneCode expectedCode) {
        if (!zone.code().equals(expectedCode) || zone.spaces().size() != SPACE_COUNT_PER_ZONE) {
            return false;
        }
        return IntStream.rangeClosed(1, SPACE_COUNT_PER_ZONE)
                .allMatch(number -> zone.spaces().get(number - 1).number().value() == number);
    }
}
