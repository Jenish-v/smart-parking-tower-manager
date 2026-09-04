package com.jenish.smartparking.pricing.domain;

import java.util.List;
import java.util.Objects;

public record ReceiptStatement(
        ParkingReceipt receipt,
        List<FeeAdjustment> adjustments,
        long adjustedTotalMinor) {

    public ReceiptStatement {
        Objects.requireNonNull(receipt, "receipt must not be null");
        adjustments = List.copyOf(Objects.requireNonNull(adjustments, "adjustments must not be null"));
        long calculatedTotal = receipt.quote().total().minorUnits();
        for (FeeAdjustment adjustment : adjustments) {
            if (!receipt.id().equals(adjustment.receiptId())) {
                throw new IllegalArgumentException("adjustment must belong to the receipt");
            }
            calculatedTotal = Math.addExact(calculatedTotal, adjustment.amountMinor());
        }
        if (calculatedTotal < 0) {
            throw new IllegalArgumentException("adjustments must not make the total negative");
        }
        if (calculatedTotal != adjustedTotalMinor) {
            throw new IllegalArgumentException("adjustedTotalMinor must match receipt and adjustments");
        }
    }

    public static ReceiptStatement from(ParkingReceipt receipt, List<FeeAdjustment> adjustments) {
        long total = receipt.quote().total().minorUnits();
        for (FeeAdjustment adjustment : adjustments) {
            total = Math.addExact(total, adjustment.amountMinor());
        }
        return new ReceiptStatement(receipt, adjustments, total);
    }
}
