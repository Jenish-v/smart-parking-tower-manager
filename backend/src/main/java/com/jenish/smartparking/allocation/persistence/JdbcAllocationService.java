package com.jenish.smartparking.allocation.persistence;

import com.jenish.smartparking.allocation.application.AllocationService;
import com.jenish.smartparking.allocation.domain.ParkingAllocation;
import com.jenish.smartparking.allocation.domain.ParkingCapacityExceededException;
import com.jenish.smartparking.allocation.domain.SpaceLocation;
import com.jenish.smartparking.allocation.domain.VehicleAlreadyParkedException;
import com.jenish.smartparking.allocation.domain.VehicleIdentifier;
import com.jenish.smartparking.allocation.domain.VehicleNotParkedException;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.FloorNumber;
import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.facility.domain.SpaceNumber;
import com.jenish.smartparking.facility.domain.ZoneCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

@Service
@Lazy
public final class JdbcAllocationService implements AllocationService {

    private static final String FIND_ACTIVE_ALLOCATION = """
            SELECT aa.id AS allocation_id,
                   aa.vehicle_identifier,
                   aa.required_size,
                   pf.facility_id,
                   pf.floor_number,
                   pz.code AS zone_code,
                   ps.space_number
            FROM active_allocations aa
            JOIN parking_spaces ps ON ps.id = aa.space_id
            JOIN parking_zones pz ON pz.id = ps.zone_id
            JOIN parking_floors pf ON pf.id = pz.floor_id
            WHERE aa.facility_id = :facilityId
              AND aa.vehicle_identifier = :vehicleIdentifier
              AND aa.released_at IS NULL
            """;

    private static final String LOCK_CANDIDATE = """
            SELECT ps.id AS space_id,
                   pf.floor_number,
                   pz.code AS zone_code,
                   ps.space_number
            FROM parking_spaces ps
            JOIN parking_zones pz ON pz.id = ps.zone_id
            JOIN parking_floors pf ON pf.id = pz.floor_id
            WHERE pf.facility_id = :facilityId
              AND ps.operational_state = 'ACTIVE'
              AND CASE ps.size_class
                      WHEN 'SMALL' THEN 1
                      WHEN 'MEDIUM' THEN 2
                      WHEN 'LARGE' THEN 3
                  END >= CASE :requiredSize
                      WHEN 'SMALL' THEN 1
                      WHEN 'MEDIUM' THEN 2
                      WHEN 'LARGE' THEN 3
                  END
              AND NOT EXISTS (
                  SELECT 1
                  FROM active_allocations aa
                  WHERE aa.space_id = ps.id
                    AND aa.released_at IS NULL
              )
            ORDER BY pf.floor_number, pz.code, ps.space_number
            LIMIT 1
            FOR UPDATE OF ps SKIP LOCKED
            """;

    private final JdbcClient jdbcClient;

    private final RetryingTransactionExecutor transactions;

    @Autowired
    public JdbcAllocationService(
            JdbcClient jdbcClient,
            PlatformTransactionManager transactionManager) {
        this(jdbcClient, new RetryingTransactionExecutor(transactionManager));
    }

    JdbcAllocationService(
            JdbcClient jdbcClient,
            RetryingTransactionExecutor transactions) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
    }

    @Override
    public ParkingAllocation park(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            SizeClass requiredSize) {
        requireArguments(facilityId, vehicleIdentifier);
        Objects.requireNonNull(requiredSize, "requiredSize must not be null");
        return transactions.execute(() -> parkOnce(facilityId, vehicleIdentifier, requiredSize));
    }

    @Override
    public Optional<ParkingAllocation> find(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier) {
        requireArguments(facilityId, vehicleIdentifier);
        return findActive(facilityId, vehicleIdentifier, false)
                .map(StoredAllocation::allocation);
    }

    @Override
    public ParkingAllocation unpark(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier) {
        requireArguments(facilityId, vehicleIdentifier);
        return transactions.execute(() -> unparkOnce(facilityId, vehicleIdentifier));
    }

    private ParkingAllocation parkOnce(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            SizeClass requiredSize) {
        if (findActive(facilityId, vehicleIdentifier, false).isPresent()) {
            throw new VehicleAlreadyParkedException(vehicleIdentifier);
        }

        CandidateSpace candidate = lockCandidate(facilityId, requiredSize)
                .orElseThrow(() -> new ParkingCapacityExceededException(requiredSize));
        ParkingAllocation allocation = new ParkingAllocation(
                vehicleIdentifier,
                requiredSize,
                candidate.location(facilityId));

        try {
            jdbcClient.sql("""
                    INSERT INTO active_allocations (
                        id,
                        facility_id,
                        space_id,
                        vehicle_identifier,
                        required_size
                    )
                    VALUES (
                        :id,
                        :facilityId,
                        :spaceId,
                        :vehicleIdentifier,
                        :requiredSize
                    )
                    """)
                    .param("id", UUID.randomUUID())
                    .param("facilityId", facilityId.value())
                    .param("spaceId", candidate.spaceId())
                    .param("vehicleIdentifier", vehicleIdentifier.value())
                    .param("requiredSize", requiredSize.name())
                    .update();
        } catch (DuplicateKeyException exception) {
            throw new VehicleAlreadyParkedException(vehicleIdentifier);
        }

        return allocation;
    }

    private ParkingAllocation unparkOnce(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier) {
        StoredAllocation stored = findActive(facilityId, vehicleIdentifier, true)
                .orElseThrow(() -> new VehicleNotParkedException(vehicleIdentifier));
        jdbcClient.sql("""
                UPDATE active_allocations
                SET released_at = CURRENT_TIMESTAMP
                WHERE id = :allocationId
                  AND released_at IS NULL
                """)
                .param("allocationId", stored.id())
                .update();
        return stored.allocation();
    }

    private Optional<StoredAllocation> findActive(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier,
            boolean lock) {
        String sql = lock ? FIND_ACTIVE_ALLOCATION + " FOR UPDATE OF aa" : FIND_ACTIVE_ALLOCATION;
        return jdbcClient.sql(sql)
                .param("facilityId", facilityId.value())
                .param("vehicleIdentifier", vehicleIdentifier.value())
                .query(this::mapStoredAllocation)
                .optional();
    }

    private Optional<CandidateSpace> lockCandidate(
            FacilityId facilityId,
            SizeClass requiredSize) {
        return jdbcClient.sql(LOCK_CANDIDATE)
                .param("facilityId", facilityId.value())
                .param("requiredSize", requiredSize.name())
                .query((resultSet, rowNumber) -> new CandidateSpace(
                        resultSet.getObject("space_id", UUID.class),
                        new FloorNumber(resultSet.getInt("floor_number")),
                        new ZoneCode(resultSet.getString("zone_code")),
                        new SpaceNumber(resultSet.getInt("space_number"))))
                .optional();
    }

    private StoredAllocation mapStoredAllocation(ResultSet resultSet, int rowNumber) throws SQLException {
        VehicleIdentifier vehicleIdentifier =
                new VehicleIdentifier(resultSet.getString("vehicle_identifier"));
        FacilityId facilityId =
                new FacilityId(resultSet.getObject("facility_id", UUID.class));
        ParkingAllocation allocation = new ParkingAllocation(
                vehicleIdentifier,
                SizeClass.valueOf(resultSet.getString("required_size")),
                new SpaceLocation(
                        facilityId,
                        new FloorNumber(resultSet.getInt("floor_number")),
                        new ZoneCode(resultSet.getString("zone_code")),
                        new SpaceNumber(resultSet.getInt("space_number"))));
        return new StoredAllocation(
                resultSet.getObject("allocation_id", UUID.class),
                allocation);
    }

    private static void requireArguments(
            FacilityId facilityId,
            VehicleIdentifier vehicleIdentifier) {
        Objects.requireNonNull(facilityId, "facilityId must not be null");
        Objects.requireNonNull(vehicleIdentifier, "vehicleIdentifier must not be null");
    }

    private record CandidateSpace(
            UUID spaceId,
            FloorNumber floorNumber,
            ZoneCode zoneCode,
            SpaceNumber spaceNumber) {

        private SpaceLocation location(FacilityId facilityId) {
            return new SpaceLocation(facilityId, floorNumber, zoneCode, spaceNumber);
        }
    }

    private record StoredAllocation(UUID id, ParkingAllocation allocation) {
    }
}
