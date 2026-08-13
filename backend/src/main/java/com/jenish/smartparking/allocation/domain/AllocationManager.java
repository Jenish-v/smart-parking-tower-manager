package com.jenish.smartparking.allocation.domain;

import com.jenish.smartparking.facility.domain.Facility;
import com.jenish.smartparking.facility.domain.ParkingFloor;
import com.jenish.smartparking.facility.domain.ParkingSpace;
import com.jenish.smartparking.facility.domain.ParkingZone;
import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.facility.domain.SpaceOperationalState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class AllocationManager {

    private final Facility facility;

    private final List<CandidateSpace> candidates;

    private final Map<VehicleIdentifier, ParkingAllocation> allocationsByVehicle = new HashMap<>();

    private final Map<SpaceLocation, VehicleIdentifier> vehiclesBySpace = new HashMap<>();

    public AllocationManager(Facility facility) {
        this.facility = Objects.requireNonNull(facility, "facility must not be null");
        candidates = buildCandidates(facility);
    }

    public ParkingAllocation park(VehicleIdentifier vehicleIdentifier, SizeClass requiredSize) {
        Objects.requireNonNull(vehicleIdentifier, "vehicleIdentifier must not be null");
        Objects.requireNonNull(requiredSize, "requiredSize must not be null");
        if (allocationsByVehicle.containsKey(vehicleIdentifier)) {
            throw new VehicleAlreadyParkedException(vehicleIdentifier);
        }

        CandidateSpace candidate = candidates.stream()
                .filter(space -> space.space().canAccept(requiredSize))
                .filter(space -> !vehiclesBySpace.containsKey(space.location()))
                .findFirst()
                .orElseThrow(() -> new ParkingCapacityExceededException(requiredSize));

        ParkingAllocation allocation = new ParkingAllocation(
                vehicleIdentifier,
                requiredSize,
                candidate.location());
        allocationsByVehicle.put(vehicleIdentifier, allocation);
        vehiclesBySpace.put(candidate.location(), vehicleIdentifier);
        return allocation;
    }

    public Optional<ParkingAllocation> find(VehicleIdentifier vehicleIdentifier) {
        Objects.requireNonNull(vehicleIdentifier, "vehicleIdentifier must not be null");
        return Optional.ofNullable(allocationsByVehicle.get(vehicleIdentifier));
    }

    public ParkingAllocation unpark(VehicleIdentifier vehicleIdentifier) {
        Objects.requireNonNull(vehicleIdentifier, "vehicleIdentifier must not be null");
        ParkingAllocation allocation = allocationsByVehicle.remove(vehicleIdentifier);
        if (allocation == null) {
            throw new VehicleNotParkedException(vehicleIdentifier);
        }
        vehiclesBySpace.remove(allocation.spaceLocation());
        return allocation;
    }

    public AvailabilitySnapshot availability() {
        Map<SizeClass, Long> availableByPhysicalSize = emptySizeCounts();
        Map<SizeClass, Long> compatibleAvailability = emptySizeCounts();
        long operationalSpaces = 0;

        for (CandidateSpace candidate : candidates) {
            if (candidate.space().operationalState() != SpaceOperationalState.ACTIVE) {
                continue;
            }
            operationalSpaces++;
            if (vehiclesBySpace.containsKey(candidate.location())) {
                continue;
            }
            availableByPhysicalSize.compute(candidate.space().sizeClass(), (key, count) -> count + 1);
            for (SizeClass requiredSize : SizeClass.values()) {
                if (candidate.space().canAccept(requiredSize)) {
                    compatibleAvailability.compute(requiredSize, (key, count) -> count + 1);
                }
            }
        }

        return new AvailabilitySnapshot(
                operationalSpaces,
                vehiclesBySpace.size(),
                availableByPhysicalSize,
                compatibleAvailability);
    }

    private static Map<SizeClass, Long> emptySizeCounts() {
        Map<SizeClass, Long> counts = new EnumMap<>(SizeClass.class);
        for (SizeClass sizeClass : SizeClass.values()) {
            counts.put(sizeClass, 0L);
        }
        return counts;
    }

    private static List<CandidateSpace> buildCandidates(Facility facility) {
        List<CandidateSpace> result = new ArrayList<>(facility.capacity());
        for (ParkingFloor floor : facility.floors()) {
            for (ParkingZone zone : floor.zones()) {
                for (ParkingSpace space : zone.spaces()) {
                    result.add(new CandidateSpace(
                            new SpaceLocation(facility.id(), floor.number(), zone.code(), space.number()),
                            space));
                }
            }
        }
        result.sort((left, right) -> left.location().compareTo(right.location()));
        return List.copyOf(result);
    }

    private record CandidateSpace(SpaceLocation location, ParkingSpace space) {
    }
}
