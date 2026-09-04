package com.jenish.smartparking.pricing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jenish.smartparking.facility.domain.SizeClass;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceiptStatementTest {

    private static final Currency CAD = Currency.getInstance("CAD");

    private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");

    private final ParkingReceipt receipt = receipt(500);

    @Test
    void appliesSignedAdjustmentsWithoutChangingTheBaseReceipt() {
        FeeAdjustment credit = adjustment(-200, AdjustmentReason.CUSTOMER_SERVICE);
        FeeAdjustment correction = adjustment(50, AdjustmentReason.RATE_CORRECTION);

        ReceiptStatement statement = ReceiptStatement.from(receipt, List.of(credit, correction));

        assertEquals(350, statement.adjustedTotalMinor());
        assertEquals(500, statement.receipt().quote().total().minorUnits());
    }

    @Test
    void rejectsAnAdjustmentThatWouldMakeTheTotalNegative() {
        FeeAdjustment credit = adjustment(-501, AdjustmentReason.CUSTOMER_SERVICE);

        assertThrows(IllegalArgumentException.class, () -> ReceiptStatement.from(receipt, List.of(credit)));
    }

    @Test
    void validatesReasonAndOperatorDetails() {
        assertThrows(IllegalArgumentException.class, () -> new FeeAdjustment(
                UUID.randomUUID(),
                receipt.id(),
                10,
                AdjustmentReason.OTHER,
                " ",
                "operator-1",
                NOW));
    }

    private FeeAdjustment adjustment(long amountMinor, AdjustmentReason reason) {
        return new FeeAdjustment(
                UUID.randomUUID(),
                receipt.id(),
                amountMinor,
                reason,
                "Documented reason",
                "operator-1",
                NOW);
    }

    private static ParkingReceipt receipt(long totalMinor) {
        UUID sessionId = UUID.randomUUID();
        Money total = new Money(totalMinor, CAD);
        FeeQuote quote = new FeeQuote(
                RatePlanId.newId(),
                1,
                SizeClass.SMALL,
                NOW.minus(Duration.ofHours(1)),
                NOW,
                Duration.ofMinutes(50),
                4,
                total,
                Money.zero(CAD),
                total);
        return new ParkingReceipt(UUID.randomUUID(), sessionId, quote, NOW);
    }
}
