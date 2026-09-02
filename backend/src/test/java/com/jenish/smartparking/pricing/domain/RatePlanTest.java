package com.jenish.smartparking.pricing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jenish.smartparking.facility.domain.SizeClass;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RatePlanTest {

    private static final Currency CAD = Currency.getInstance("CAD");

    private static final Instant EFFECTIVE_FROM = Instant.parse("2026-09-01T00:00:00Z");

    private static final Instant ENTRY = Instant.parse("2026-09-02T12:00:00Z");

    private final RatePlan plan = ratePlan(Duration.ofMinutes(10));

    @Test
    void doesNotChargeAStayWithinTheGracePeriod() {
        FeeQuote quote = plan.quote(SizeClass.SMALL, ENTRY, ENTRY.plus(Duration.ofMinutes(10)));

        assertEquals(Duration.ZERO, quote.billableDuration());
        assertEquals(0, quote.billingIncrements());
        assertEquals(new Money(0, CAD), quote.total());
    }

    @Test
    void roundsBillableTimeUpToTheNextIncrement() {
        FeeQuote firstIncrement = plan.quote(SizeClass.SMALL, ENTRY, ENTRY.plus(Duration.ofMinutes(10)).plusSeconds(1));
        FeeQuote secondIncrement = plan.quote(
                SizeClass.SMALL,
                ENTRY,
                ENTRY.plus(Duration.ofMinutes(25)).plusSeconds(1));

        assertEquals(1, firstIncrement.billingIncrements());
        assertEquals(new Money(125, CAD), firstIncrement.total());
        assertEquals(2, secondIncrement.billingIncrements());
        assertEquals(new Money(250, CAD), secondIncrement.total());
    }

    @Test
    void appliesTheCapToEachRollingDay() {
        RatePlan noGrace = ratePlan(Duration.ZERO);

        FeeQuote quote = noGrace.quote(SizeClass.SMALL, ENTRY, ENTRY.plus(Duration.ofHours(25)));

        assertEquals(100, quote.billingIncrements());
        assertEquals(new Money(12_500, CAD), quote.grossCharge());
        assertEquals(new Money(10_000, CAD), quote.capDiscount());
        assertEquals(new Money(2_500, CAD), quote.total());
    }

    @Test
    void selectsTheRateForTheVehicleSize() {
        FeeQuote quote = plan.quote(SizeClass.LARGE, ENTRY, ENTRY.plus(Duration.ofMinutes(11)));

        assertEquals(new Money(200, CAD), quote.total());
    }

    @Test
    void usesAHalfOpenEffectiveWindow() {
        RatePlan expiring = new RatePlan(
                RatePlanId.newId(),
                2,
                "Autumn",
                EFFECTIVE_FROM,
                ENTRY,
                Duration.ZERO,
                Duration.ofMinutes(15),
                rates());

        assertTrue(expiring.appliesAt(EFFECTIVE_FROM));
        assertFalse(expiring.appliesAt(ENTRY));
        assertThrows(IllegalArgumentException.class, () -> expiring.quote(SizeClass.SMALL, ENTRY, ENTRY));
    }

    @Test
    void rejectsInvalidTimesAndIncompleteRates() {
        assertThrows(
                IllegalArgumentException.class,
                () -> plan.quote(SizeClass.SMALL, ENTRY, ENTRY.minusSeconds(1)));

        Map<SizeClass, RateBand> incompleteRates = new EnumMap<>(SizeClass.class);
        incompleteRates.put(SizeClass.SMALL, rates().get(SizeClass.SMALL));
        assertThrows(IllegalArgumentException.class, () -> new RatePlan(
                RatePlanId.newId(),
                1,
                "Incomplete",
                EFFECTIVE_FROM,
                null,
                Duration.ZERO,
                Duration.ofMinutes(15),
                incompleteRates));
    }

    @Test
    void protectsMoneyCurrencyAndOverflowBoundaries() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Money(100, CAD).add(new Money(100, Currency.getInstance("USD"))));
        assertThrows(ArithmeticException.class, () -> new Money(Long.MAX_VALUE, CAD).add(new Money(1, CAD)));
    }

    private static RatePlan ratePlan(Duration gracePeriod) {
        return new RatePlan(
                RatePlanId.newId(),
                1,
                "Standard",
                EFFECTIVE_FROM,
                null,
                gracePeriod,
                Duration.ofMinutes(15),
                rates());
    }

    private static Map<SizeClass, RateBand> rates() {
        Map<SizeClass, RateBand> rates = new EnumMap<>(SizeClass.class);
        rates.put(SizeClass.SMALL, new RateBand(new Money(125, CAD), new Money(2_000, CAD)));
        rates.put(SizeClass.MEDIUM, new RateBand(new Money(150, CAD), new Money(2_500, CAD)));
        rates.put(SizeClass.LARGE, new RateBand(new Money(200, CAD), new Money(3_000, CAD)));
        return rates;
    }
}
