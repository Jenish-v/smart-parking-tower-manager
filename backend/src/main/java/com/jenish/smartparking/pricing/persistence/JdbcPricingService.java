package com.jenish.smartparking.pricing.persistence;

import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.pricing.application.AdjustmentIdentifierConflictException;
import com.jenish.smartparking.pricing.application.NegativeAdjustedTotalException;
import com.jenish.smartparking.pricing.application.NoApplicableRatePlanException;
import com.jenish.smartparking.pricing.application.PricingService;
import com.jenish.smartparking.pricing.application.ReceiptNotFoundException;
import com.jenish.smartparking.pricing.domain.AdjustmentReason;
import com.jenish.smartparking.pricing.domain.FeeAdjustment;
import com.jenish.smartparking.pricing.domain.FeeQuote;
import com.jenish.smartparking.pricing.domain.Money;
import com.jenish.smartparking.pricing.domain.ParkingReceipt;
import com.jenish.smartparking.pricing.domain.RateBand;
import com.jenish.smartparking.pricing.domain.RatePlan;
import com.jenish.smartparking.pricing.domain.RatePlanId;
import com.jenish.smartparking.pricing.domain.ReceiptStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Lazy
public final class JdbcPricingService implements PricingService {

    private static final String RECEIPT_COLUMNS = """
            SELECT id,
                   session_id,
                   rate_plan_id,
                   rate_plan_version,
                   size_class,
                   entered_at,
                   exited_at,
                   billable_seconds,
                   billable_nanos,
                   billing_increments,
                   gross_charge_minor,
                   cap_discount_minor,
                   total_minor,
                   currency,
                   issued_at
            FROM parking_receipts
            """;

    private final JdbcClient jdbcClient;

    private final Clock clock;

    private final TransactionOperations transactions;

    @Autowired
    public JdbcPricingService(
            JdbcClient jdbcClient,
            PlatformTransactionManager transactionManager) {
        this(jdbcClient, Clock.systemUTC(), new TransactionTemplate(transactionManager));
    }

    JdbcPricingService(
            JdbcClient jdbcClient,
            Clock clock,
            TransactionOperations transactions) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
    }

    @Override
    public ParkingReceipt assess(
            UUID sessionId,
            SizeClass sizeClass,
            Instant enteredAt,
            Instant exitedAt) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(sizeClass, "sizeClass must not be null");
        Objects.requireNonNull(enteredAt, "enteredAt must not be null");
        Objects.requireNonNull(exitedAt, "exitedAt must not be null");

        Optional<ParkingReceipt> existing = findReceipt(sessionId);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }

        RatePlan plan = findApplicablePlan(enteredAt)
                .orElseThrow(() -> new NoApplicableRatePlanException(enteredAt));
        FeeQuote quote = plan.quote(sizeClass, enteredAt, exitedAt);
        ParkingReceipt receipt = new ParkingReceipt(
                UUID.randomUUID(),
                sessionId,
                quote,
                now());
        insertReceipt(receipt);
        return receipt;
    }

    @Override
    public Optional<ParkingReceipt> findReceipt(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return jdbcClient.sql(RECEIPT_COLUMNS + " WHERE session_id = :sessionId")
                .param("sessionId", sessionId)
                .query(this::mapReceipt)
                .optional();
    }

    @Override
    public Map<UUID, ParkingReceipt> findReceipts(Collection<UUID> sessionIds) {
        Objects.requireNonNull(sessionIds, "sessionIds must not be null");
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ParkingReceipt> receipts = new java.util.HashMap<>();
        jdbcClient.sql(RECEIPT_COLUMNS + " WHERE session_id IN (:sessionIds)")
                .param("sessionIds", sessionIds)
                .query(this::mapReceipt)
                .list()
                .forEach(receipt -> receipts.put(receipt.sessionId(), receipt));
        return Map.copyOf(receipts);
    }

    @Override
    public ReceiptStatement findStatement(FacilityId facilityId, UUID sessionId) {
        requireFacilityAndSession(facilityId, sessionId);
        ParkingReceipt receipt = findFacilityReceipt(facilityId, sessionId)
                .orElseThrow(() -> new ReceiptNotFoundException(sessionId));
        return ReceiptStatement.from(receipt, findAdjustments(receipt.id()));
    }

    @Override
    public ReceiptStatement adjust(
            FacilityId facilityId,
            UUID sessionId,
            UUID adjustmentId,
            long amountMinor,
            AdjustmentReason reason,
            String reasonDetail,
            String operatorReference) {
        requireFacilityAndSession(facilityId, sessionId);
        Objects.requireNonNull(adjustmentId, "adjustmentId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        return transactions.execute(status -> adjustOnce(
                facilityId,
                sessionId,
                adjustmentId,
                amountMinor,
                reason,
                reasonDetail,
                operatorReference));
    }

    private ReceiptStatement adjustOnce(
            FacilityId facilityId,
            UUID sessionId,
            UUID adjustmentId,
            long amountMinor,
            AdjustmentReason reason,
            String reasonDetail,
            String operatorReference) {
        lockAdjustmentIdentifier(adjustmentId);
        ParkingReceipt receipt = lockFacilityReceipt(facilityId, sessionId)
                .orElseThrow(() -> new ReceiptNotFoundException(sessionId));
        FeeAdjustment requested = new FeeAdjustment(
                adjustmentId,
                receipt.id(),
                amountMinor,
                reason,
                reasonDetail,
                operatorReference,
                now());
        Optional<FeeAdjustment> existing = findAdjustment(adjustmentId);
        if (existing.isPresent()) {
            return replayAdjustment(existing.orElseThrow(), requested, receipt);
        }

        ArrayList<FeeAdjustment> adjustments = new ArrayList<>(findAdjustments(receipt.id()));
        long currentTotal = ReceiptStatement.from(receipt, adjustments).adjustedTotalMinor();
        if (Math.addExact(currentTotal, amountMinor) < 0) {
            throw new NegativeAdjustedTotalException();
        }
        insertAdjustment(requested);
        adjustments.add(requested);
        return ReceiptStatement.from(receipt, adjustments);
    }

    private ReceiptStatement replayAdjustment(
            FeeAdjustment existing,
            FeeAdjustment requested,
            ParkingReceipt receipt) {
        if (!existing.receiptId().equals(requested.receiptId())
                || existing.amountMinor() != requested.amountMinor()
                || existing.reason() != requested.reason()
                || !existing.reasonDetail().equals(requested.reasonDetail())
                || !existing.operatorReference().equals(requested.operatorReference())) {
            throw new AdjustmentIdentifierConflictException(requested.id());
        }
        return ReceiptStatement.from(receipt, findAdjustments(receipt.id()));
    }

    private Optional<RatePlan> findApplicablePlan(Instant enteredAt) {
        Optional<RatePlanHeader> header = jdbcClient.sql("""
                SELECT id,
                       version,
                       name,
                       effective_from,
                       effective_until,
                       grace_seconds,
                       billing_increment_seconds,
                       currency
                FROM pricing_rate_plans
                WHERE effective_from <= :enteredAt
                  AND (effective_until IS NULL OR :enteredAt < effective_until)
                """)
                .param("enteredAt", databaseTime(enteredAt))
                .query((resultSet, rowNumber) -> new RatePlanHeader(
                        new RatePlanId(resultSet.getObject("id", UUID.class)),
                        resultSet.getLong("version"),
                        resultSet.getString("name"),
                        resultSet.getObject("effective_from", OffsetDateTime.class).toInstant(),
                        optionalInstant(resultSet, "effective_until"),
                        Duration.ofSeconds(resultSet.getLong("grace_seconds")),
                        Duration.ofSeconds(resultSet.getLong("billing_increment_seconds")),
                        Currency.getInstance(resultSet.getString("currency").trim())))
                .optional();
        return header.map(this::loadRatePlan);
    }

    private RatePlan loadRatePlan(RatePlanHeader header) {
        return new RatePlan(
                header.id(),
                header.version(),
                header.name(),
                header.effectiveFrom(),
                header.effectiveUntil(),
                header.gracePeriod(),
                header.billingIncrement(),
                loadRates(header.id(), header.version(), header.currency()));
    }

    private Map<SizeClass, RateBand> loadRates(
            RatePlanId ratePlanId,
            long version,
            Currency currency) {
        Map<SizeClass, RateBand> rates = new EnumMap<>(SizeClass.class);
        jdbcClient.sql("""
                SELECT size_class, increment_charge_minor, rolling_day_cap_minor
                FROM pricing_rate_bands
                WHERE rate_plan_id = :ratePlanId
                  AND rate_plan_version = :version
                """)
                .param("ratePlanId", ratePlanId.value())
                .param("version", version)
                .query(resultSet -> {
                    while (resultSet.next()) {
                        rates.put(
                                SizeClass.valueOf(resultSet.getString("size_class")),
                                new RateBand(
                                        new Money(resultSet.getLong("increment_charge_minor"), currency),
                                        new Money(resultSet.getLong("rolling_day_cap_minor"), currency)));
                    }
                    return rates;
                });
        return rates;
    }

    private void insertReceipt(ParkingReceipt receipt) {
        FeeQuote quote = receipt.quote();
        jdbcClient.sql("""
                INSERT INTO parking_receipts (
                    id, session_id, rate_plan_id, rate_plan_version, size_class,
                    entered_at, exited_at, billable_seconds, billable_nanos, billing_increments,
                    gross_charge_minor, cap_discount_minor, total_minor, currency, issued_at
                ) VALUES (
                    :id, :sessionId, :ratePlanId, :ratePlanVersion, :sizeClass,
                    :enteredAt, :exitedAt, :billableSeconds, :billableNanos, :billingIncrements,
                    :grossCharge, :capDiscount, :total, :currency, :issuedAt
                )
                """)
                .param("id", receipt.id())
                .param("sessionId", receipt.sessionId())
                .param("ratePlanId", quote.ratePlanId().value())
                .param("ratePlanVersion", quote.ratePlanVersion())
                .param("sizeClass", quote.sizeClass().name())
                .param("enteredAt", databaseTime(quote.enteredAt()))
                .param("exitedAt", databaseTime(quote.exitedAt()))
                .param("billableSeconds", quote.billableDuration().getSeconds())
                .param("billableNanos", quote.billableDuration().getNano())
                .param("billingIncrements", quote.billingIncrements())
                .param("grossCharge", quote.grossCharge().minorUnits())
                .param("capDiscount", quote.capDiscount().minorUnits())
                .param("total", quote.total().minorUnits())
                .param("currency", quote.total().currency().getCurrencyCode())
                .param("issuedAt", databaseTime(receipt.issuedAt()))
                .update();
    }

    private Optional<ParkingReceipt> findFacilityReceipt(
            FacilityId facilityId,
            UUID sessionId) {
        return jdbcClient.sql(RECEIPT_COLUMNS + """
                WHERE session_id = :sessionId
                  AND EXISTS (
                      SELECT 1
                      FROM parking_sessions ps
                      WHERE ps.id = parking_receipts.session_id
                        AND ps.facility_id = :facilityId
                  )
                """)
                .param("sessionId", sessionId)
                .param("facilityId", facilityId.value())
                .query(this::mapReceipt)
                .optional();
    }

    private Optional<ParkingReceipt> lockFacilityReceipt(
            FacilityId facilityId,
            UUID sessionId) {
        return jdbcClient.sql(RECEIPT_COLUMNS + """
                WHERE session_id = :sessionId
                  AND EXISTS (
                      SELECT 1
                      FROM parking_sessions ps
                      WHERE ps.id = parking_receipts.session_id
                        AND ps.facility_id = :facilityId
                  )
                FOR UPDATE
                """)
                .param("sessionId", sessionId)
                .param("facilityId", facilityId.value())
                .query(this::mapReceipt)
                .optional();
    }

    private List<FeeAdjustment> findAdjustments(UUID receiptId) {
        return jdbcClient.sql("""
                SELECT id,
                       receipt_id,
                       amount_minor,
                       reason,
                       reason_detail,
                       operator_reference,
                       created_at
                FROM fee_adjustments
                WHERE receipt_id = :receiptId
                ORDER BY created_at, id
                """)
                .param("receiptId", receiptId)
                .query(this::mapAdjustment)
                .list();
    }

    private Optional<FeeAdjustment> findAdjustment(UUID adjustmentId) {
        return jdbcClient.sql("""
                SELECT id,
                       receipt_id,
                       amount_minor,
                       reason,
                       reason_detail,
                       operator_reference,
                       created_at
                FROM fee_adjustments
                WHERE id = :adjustmentId
                """)
                .param("adjustmentId", adjustmentId)
                .query(this::mapAdjustment)
                .optional();
    }

    private void lockAdjustmentIdentifier(UUID adjustmentId) {
        jdbcClient.sql("""
                SELECT pg_advisory_xact_lock(
                    hashtextextended(:adjustmentId, 2)
                )
                """)
                .param("adjustmentId", adjustmentId.toString())
                .query(Long.class)
                .single();
    }

    private void insertAdjustment(FeeAdjustment adjustment) {
        jdbcClient.sql("""
                INSERT INTO fee_adjustments (
                    id, receipt_id, amount_minor, reason,
                    reason_detail, operator_reference, created_at
                ) VALUES (
                    :id, :receiptId, :amountMinor, :reason,
                    :reasonDetail, :operatorReference, :createdAt
                )
                """)
                .param("id", adjustment.id())
                .param("receiptId", adjustment.receiptId())
                .param("amountMinor", adjustment.amountMinor())
                .param("reason", adjustment.reason().name())
                .param("reasonDetail", adjustment.reasonDetail())
                .param("operatorReference", adjustment.operatorReference())
                .param("createdAt", databaseTime(adjustment.createdAt()))
                .update();
    }

    private ParkingReceipt mapReceipt(ResultSet resultSet, int rowNumber) throws SQLException {
        Currency currency = Currency.getInstance(resultSet.getString("currency").trim());
        FeeQuote quote = new FeeQuote(
                new RatePlanId(resultSet.getObject("rate_plan_id", UUID.class)),
                resultSet.getLong("rate_plan_version"),
                SizeClass.valueOf(resultSet.getString("size_class")),
                resultSet.getObject("entered_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("exited_at", OffsetDateTime.class).toInstant(),
                Duration.ofSeconds(
                        resultSet.getLong("billable_seconds"),
                        resultSet.getInt("billable_nanos")),
                resultSet.getLong("billing_increments"),
                new Money(resultSet.getLong("gross_charge_minor"), currency),
                new Money(resultSet.getLong("cap_discount_minor"), currency),
                new Money(resultSet.getLong("total_minor"), currency));
        return new ParkingReceipt(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("session_id", UUID.class),
                quote,
                resultSet.getObject("issued_at", OffsetDateTime.class).toInstant());
    }

    private FeeAdjustment mapAdjustment(ResultSet resultSet, int rowNumber) throws SQLException {
        return new FeeAdjustment(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("receipt_id", UUID.class),
                resultSet.getLong("amount_minor"),
                AdjustmentReason.valueOf(resultSet.getString("reason")),
                resultSet.getString("reason_detail"),
                resultSet.getString("operator_reference"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static Instant optionalInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static void requireFacilityAndSession(
            FacilityId facilityId,
            UUID sessionId) {
        Objects.requireNonNull(facilityId, "facilityId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
    }

    private record RatePlanHeader(
            RatePlanId id,
            long version,
            String name,
            Instant effectiveFrom,
            Instant effectiveUntil,
            Duration gracePeriod,
            Duration billingIncrement,
            Currency currency) {
    }
}
