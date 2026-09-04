package com.jenish.smartparking.pricing.web;

import com.jenish.smartparking.pricing.domain.FeeAdjustment;
import com.jenish.smartparking.pricing.domain.ParkingReceipt;
import com.jenish.smartparking.pricing.domain.ReceiptStatement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReceiptStatementResponse(
        UUID receiptId,
        UUID sessionId,
        UUID ratePlanId,
        long ratePlanVersion,
        String sizeClass,
        Instant enteredAt,
        Instant exitedAt,
        String billableDuration,
        long billingIncrements,
        long grossChargeMinor,
        long capDiscountMinor,
        long baseTotalMinor,
        long adjustedTotalMinor,
        String currency,
        Instant issuedAt,
        List<AdjustmentResponse> adjustments) {

    public static ReceiptStatementResponse from(ReceiptStatement statement) {
        ParkingReceipt receipt = statement.receipt();
        return new ReceiptStatementResponse(
                receipt.id(),
                receipt.sessionId(),
                receipt.quote().ratePlanId().value(),
                receipt.quote().ratePlanVersion(),
                receipt.quote().sizeClass().name(),
                receipt.quote().enteredAt(),
                receipt.quote().exitedAt(),
                receipt.quote().billableDuration().toString(),
                receipt.quote().billingIncrements(),
                receipt.quote().grossCharge().minorUnits(),
                receipt.quote().capDiscount().minorUnits(),
                receipt.quote().total().minorUnits(),
                statement.adjustedTotalMinor(),
                receipt.quote().total().currency().getCurrencyCode(),
                receipt.issuedAt(),
                statement.adjustments().stream().map(AdjustmentResponse::from).toList());
    }

    public record AdjustmentResponse(
            UUID adjustmentId,
            long amountMinor,
            String reason,
            String reasonDetail,
            String operatorReference,
            Instant createdAt) {

        private static AdjustmentResponse from(FeeAdjustment adjustment) {
            return new AdjustmentResponse(
                    adjustment.id(),
                    adjustment.amountMinor(),
                    adjustment.reason().name(),
                    adjustment.reasonDetail(),
                    adjustment.operatorReference(),
                    adjustment.createdAt());
        }
    }
}
