package com.jenish.smartparking.pricing.domain;

import com.jenish.smartparking.facility.domain.SizeClass;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record RatePlan(
        RatePlanId id,
        long version,
        String name,
        Instant effectiveFrom,
        Instant effectiveUntil,
        Duration gracePeriod,
        Duration billingIncrement,
        Map<SizeClass, RateBand> rates) {

    private static final Duration ROLLING_DAY = Duration.ofHours(24);

    public RatePlan {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        Objects.requireNonNull(gracePeriod, "gracePeriod must not be null");
        Objects.requireNonNull(billingIncrement, "billingIncrement must not be null");
        Objects.requireNonNull(rates, "rates must not be null");
        name = name.trim();
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (name.isEmpty() || name.length() > 80) {
            throw new IllegalArgumentException("name must contain between 1 and 80 characters");
        }
        if (effectiveUntil != null && !effectiveUntil.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveUntil must be after effectiveFrom");
        }
        if (gracePeriod.isNegative() || gracePeriod.compareTo(ROLLING_DAY) >= 0) {
            throw new IllegalArgumentException("gracePeriod must be between zero and 24 hours");
        }
        if (billingIncrement.isZero()
                || billingIncrement.isNegative()
                || billingIncrement.compareTo(ROLLING_DAY) > 0) {
            throw new IllegalArgumentException("billingIncrement must be between zero and 24 hours");
        }
        if (gracePeriod.toNanosPart() != 0 || billingIncrement.toNanosPart() != 0) {
            throw new IllegalArgumentException("pricing durations must use whole seconds");
        }
        if (!rates.keySet().equals(Set.of(SizeClass.values()))) {
            throw new IllegalArgumentException("rates must contain every size class and no other keys");
        }
        rates = Map.copyOf(new EnumMap<>(rates));
        Currency currency = rates.values().iterator().next().incrementCharge().currency();
        if (rates.values().stream().anyMatch(rate -> !currency.equals(rate.incrementCharge().currency()))) {
            throw new IllegalArgumentException("all rate bands must use the same currency");
        }
    }

    public boolean appliesAt(Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        return !instant.isBefore(effectiveFrom) && (effectiveUntil == null || instant.isBefore(effectiveUntil));
    }

    public FeeQuote quote(SizeClass sizeClass, Instant enteredAt, Instant exitedAt) {
        Objects.requireNonNull(sizeClass, "sizeClass must not be null");
        Objects.requireNonNull(enteredAt, "enteredAt must not be null");
        Objects.requireNonNull(exitedAt, "exitedAt must not be null");
        if (!appliesAt(enteredAt)) {
            throw new IllegalArgumentException("rate plan does not apply at entry time");
        }
        Duration stay = Duration.between(enteredAt, exitedAt);
        if (stay.isNegative()) {
            throw new IllegalArgumentException("exitedAt must not precede enteredAt");
        }

        RateBand rate = rates.get(sizeClass);
        if (stay.compareTo(gracePeriod) <= 0) {
            Money zero = Money.zero(rate.incrementCharge().currency());
            return new FeeQuote(id, version, sizeClass, enteredAt, exitedAt, Duration.ZERO, 0, zero, zero, zero);
        }

        Duration billableDuration = stay.minus(gracePeriod);
        long fullDays = billableDuration.dividedBy(ROLLING_DAY);
        Duration remainder = billableDuration.minus(ROLLING_DAY.multipliedBy(fullDays));
        long fullDayIncrements = ceilingDividedBy(ROLLING_DAY, billingIncrement);
        long increments = Math.multiplyExact(fullDays, fullDayIncrements);
        Money fullDayGross = rate.incrementCharge().multiply(fullDayIncrements);
        Money gross = fullDayGross.multiply(fullDays);
        Money total = fullDayGross.min(rate.rollingDayCap()).multiply(fullDays);

        if (!remainder.isZero()) {
            long remainderIncrements = ceilingDividedBy(remainder, billingIncrement);
            Money remainderGross = rate.incrementCharge().multiply(remainderIncrements);
            increments = Math.addExact(increments, remainderIncrements);
            gross = gross.add(remainderGross);
            total = total.add(remainderGross.min(rate.rollingDayCap()));
        }

        return new FeeQuote(
                id,
                version,
                sizeClass,
                enteredAt,
                exitedAt,
                billableDuration,
                increments,
                gross,
                gross.subtract(total),
                total);
    }

    private static long ceilingDividedBy(Duration value, Duration divisor) {
        long quotient = value.dividedBy(divisor);
        return divisor.multipliedBy(quotient).equals(value) ? quotient : Math.incrementExact(quotient);
    }
}
