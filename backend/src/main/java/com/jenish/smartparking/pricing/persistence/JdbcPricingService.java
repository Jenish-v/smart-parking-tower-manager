package com.jenish.smartparking.pricing.persistence;

import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.pricing.application.NoApplicableRatePlanException;
import com.jenish.smartparking.pricing.application.PricingService;
import com.jenish.smartparking.pricing.domain.FeeQuote;
import com.jenish.smartparking.pricing.domain.Money;
import com.jenish.smartparking.pricing.domain.ParkingReceipt;
import com.jenish.smartparking.pricing.domain.RateBand;
import com.jenish.smartparking.pricing.domain.RatePlan;
import com.jenish.smartparking.pricing.domain.RatePlanId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

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

    @Autowired
    public JdbcPricingService(JdbcClient jdbcClient) {
        this(jdbcClient, Clock.systemUTC());
    }

    JdbcPricingService(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
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
