package com.jenish.smartparking.pricing.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.pricing.application.AdjustmentIdentifierConflictException;
import com.jenish.smartparking.pricing.application.NegativeAdjustedTotalException;
import com.jenish.smartparking.pricing.application.NoApplicableRatePlanException;
import com.jenish.smartparking.pricing.application.PricingService;
import com.jenish.smartparking.pricing.domain.Money;
import com.jenish.smartparking.pricing.domain.ParkingReceipt;
import com.jenish.smartparking.pricing.domain.AdjustmentReason;
import com.jenish.smartparking.pricing.domain.ReceiptStatement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/devdata")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class JdbcPricingServiceTest {

    private static final UUID FACILITY_ID =
            UUID.fromString("d936bb7d-3027-47aa-a47b-d04a37e07310");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired
    private PricingService pricingService;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void persistsAndReplaysAReceiptFromTheApplicableRatePlan() {
        Instant exitedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant enteredAt = exitedAt.minus(35, ChronoUnit.MINUTES);
        UUID sessionId = insertSession(enteredAt, exitedAt);

        ParkingReceipt receipt = pricingService.assess(sessionId, SizeClass.SMALL, enteredAt, exitedAt);
        ParkingReceipt replay = pricingService.assess(sessionId, SizeClass.SMALL, enteredAt, exitedAt);

        assertEquals(receipt, replay);
        assertEquals(new Money(250, Currency.getInstance("CAD")), receipt.quote().total());
        assertEquals(2, receipt.quote().billingIncrements());
        assertEquals(1L, count("parking_receipts"));
        assertEquals(receipt, pricingService.findReceipt(sessionId).orElseThrow());
    }

    @Test
    void failsWhenNoRatePlanAppliedAtEntry() {
        jdbcClient.sql("DELETE FROM pricing_rate_bands").update();
        jdbcClient.sql("DELETE FROM pricing_rate_plans").update();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID sessionId = insertSession(now, now);

        assertThrows(
                NoApplicableRatePlanException.class,
                () -> pricingService.assess(sessionId, SizeClass.SMALL, now, now));
        assertEquals(0L, count("parking_receipts"));
    }

    @Test
    void preventsOverlappingRatePlanVersions() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcClient.sql("""
                INSERT INTO pricing_rate_plans (
                    id, version, name, effective_from, effective_until,
                    grace_seconds, billing_increment_seconds, currency
                ) VALUES (
                    :id, 1, 'Overlap', '2026-01-01T00:00:00Z', NULL,
                    0, 900, 'CAD'
                )
                """)
                .param("id", UUID.randomUUID())
                .update());
    }

    @Test
    void persistsAndReplaysAReasonCodedAdjustment() {
        Instant exitedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant enteredAt = exitedAt.minus(35, ChronoUnit.MINUTES);
        UUID sessionId = insertSession(enteredAt, exitedAt);
        pricingService.assess(sessionId, SizeClass.SMALL, enteredAt, exitedAt);
        UUID adjustmentId = UUID.randomUUID();

        ReceiptStatement adjusted = pricingService.adjust(
                new FacilityId(FACILITY_ID),
                sessionId,
                adjustmentId,
                -100,
                AdjustmentReason.CUSTOMER_SERVICE,
                "Validated service recovery",
                "operator-1");
        ReceiptStatement replay = pricingService.adjust(
                new FacilityId(FACILITY_ID),
                sessionId,
                adjustmentId,
                -100,
                AdjustmentReason.CUSTOMER_SERVICE,
                "Validated service recovery",
                "operator-1");

        assertEquals(adjusted, replay);
        assertEquals(150, adjusted.adjustedTotalMinor());
        assertEquals(1L, count("fee_adjustments"));
        assertEquals(adjusted, pricingService.findStatement(new FacilityId(FACILITY_ID), sessionId));
        assertEquals(1, pricingService.findReceipts(java.util.List.of(sessionId)).size());
    }

    @Test
    void rejectsAdjustmentIdentifierReuseAndANegativeTotal() {
        Instant exitedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant enteredAt = exitedAt.minus(35, ChronoUnit.MINUTES);
        UUID sessionId = insertSession(enteredAt, exitedAt);
        pricingService.assess(sessionId, SizeClass.SMALL, enteredAt, exitedAt);
        UUID adjustmentId = UUID.randomUUID();
        pricingService.adjust(
                new FacilityId(FACILITY_ID),
                sessionId,
                adjustmentId,
                -100,
                AdjustmentReason.CUSTOMER_SERVICE,
                "Validated service recovery",
                "operator-1");

        assertThrows(AdjustmentIdentifierConflictException.class, () -> pricingService.adjust(
                new FacilityId(FACILITY_ID),
                sessionId,
                adjustmentId,
                100,
                AdjustmentReason.CUSTOMER_SERVICE,
                "Validated service recovery",
                "operator-1"));
        assertThrows(NegativeAdjustedTotalException.class, () -> pricingService.adjust(
                new FacilityId(FACILITY_ID),
                sessionId,
                UUID.randomUUID(),
                -151,
                AdjustmentReason.CUSTOMER_SERVICE,
                "Excessive credit",
                "operator-1"));
    }

    private UUID insertSession(Instant enteredAt, Instant exitedAt) {
        UUID sessionId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO parking_sessions (
                    id, facility_id, vehicle_identifier, required_size,
                    floor_number, zone_code, space_number, status, entered_at, exited_at
                ) VALUES (
                    :id, :facilityId, :vehicleIdentifier, 'SMALL',
                    1, 'A', 1, 'COMPLETED', :enteredAt, :exitedAt
                )
                """)
                .param("id", sessionId)
                .param("facilityId", FACILITY_ID)
                .param("vehicleIdentifier", "PRICE " + sessionId.toString().substring(0, 8).toUpperCase())
                .param("enteredAt", databaseTime(enteredAt))
                .param("exitedAt", databaseTime(exitedAt))
                .update();
        return sessionId;
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
