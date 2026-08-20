package com.jenish.smartparking.allocation.persistence;

import com.jenish.smartparking.allocation.application.OccupancyService;
import com.jenish.smartparking.allocation.application.OccupancySnapshot;
import com.jenish.smartparking.allocation.application.OccupancySnapshot.FloorOccupancy;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.facility.domain.FacilityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Lazy
public class JdbcOccupancyService implements OccupancyService {

    private static final String FLOOR_OCCUPANCY = """
            SELECT pf.floor_number,
                   count(ps.id) AS total_spaces,
                   count(ps.id) FILTER (
                       WHERE ps.operational_state = 'ACTIVE'
                   ) AS operational_spaces,
                   count(aa.id) AS occupied_spaces,
                   count(ps.id) FILTER (
                       WHERE ps.operational_state = 'ACTIVE'
                         AND aa.id IS NULL
                   ) AS available_spaces
            FROM parking_floors pf
            JOIN parking_zones pz ON pz.floor_id = pf.id
            JOIN parking_spaces ps ON ps.zone_id = pz.id
            LEFT JOIN active_allocations aa
                ON aa.space_id = ps.id
               AND aa.released_at IS NULL
            WHERE pf.facility_id = :facilityId
            GROUP BY pf.floor_number
            ORDER BY pf.floor_number
            """;

    private final JdbcClient jdbcClient;

    public JdbcOccupancyService(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public OccupancySnapshot getOccupancy(FacilityId facilityId) {
        Objects.requireNonNull(facilityId, "facilityId must not be null");
        if (!facilityExists(facilityId)) {
            throw new FacilityNotFoundException(facilityId);
        }

        List<FloorOccupancy> floors = jdbcClient.sql(FLOOR_OCCUPANCY)
                .param("facilityId", facilityId.value())
                .query((resultSet, rowNumber) -> new FloorOccupancy(
                        resultSet.getInt("floor_number"),
                        resultSet.getLong("total_spaces"),
                        resultSet.getLong("operational_spaces"),
                        resultSet.getLong("occupied_spaces"),
                        resultSet.getLong("available_spaces")))
                .list();

        return new OccupancySnapshot(
                facilityId,
                Instant.now(),
                floors.stream().mapToLong(FloorOccupancy::totalSpaces).sum(),
                floors.stream().mapToLong(FloorOccupancy::operationalSpaces).sum(),
                floors.stream().mapToLong(FloorOccupancy::occupiedSpaces).sum(),
                floors.stream().mapToLong(FloorOccupancy::availableSpaces).sum(),
                floors);
    }

    private boolean facilityExists(FacilityId facilityId) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM facilities WHERE id = :facilityId)")
                .param("facilityId", facilityId.value())
                .query(Boolean.class)
                .single();
    }
}
