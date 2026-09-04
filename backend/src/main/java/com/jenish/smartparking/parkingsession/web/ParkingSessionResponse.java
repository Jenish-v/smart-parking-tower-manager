package com.jenish.smartparking.parkingsession.web;

import com.jenish.smartparking.parkingsession.domain.ParkingSession;
import com.jenish.smartparking.parkingsession.application.ParkingSessionExit;
import com.jenish.smartparking.pricing.domain.ParkingReceipt;
import java.time.Instant;
import java.util.UUID;

public record ParkingSessionResponse(
        UUID sessionId,
        UUID facilityId,
        String vehicleIdentifier,
        String requiredSize,
        String status,
        SpaceResponse space,
        Instant enteredAt,
        Instant exitedAt,
        UUID reservationId,
        ReceiptResponse receipt) {

    public static ParkingSessionResponse from(ParkingSession session) {
        return new ParkingSessionResponse(
                session.id().value(),
                session.facilityId().value(),
                session.vehicleIdentifier().value(),
                session.requiredSize().name(),
                session.status().name(),
                new SpaceResponse(
                        session.spaceLocation().floorNumber().value(),
                        session.spaceLocation().zoneCode().value(),
                        session.spaceLocation().spaceNumber().value()),
                session.enteredAt(),
                session.exitedAt(),
                session.reservationId() == null ? null : session.reservationId().value(),
                null);
    }

    public static ParkingSessionResponse from(ParkingSessionExit result) {
        ParkingSession session = result.session();
        return new ParkingSessionResponse(
                session.id().value(),
                session.facilityId().value(),
                session.vehicleIdentifier().value(),
                session.requiredSize().name(),
                session.status().name(),
                new SpaceResponse(
                        session.spaceLocation().floorNumber().value(),
                        session.spaceLocation().zoneCode().value(),
                        session.spaceLocation().spaceNumber().value()),
                session.enteredAt(),
                session.exitedAt(),
                session.reservationId() == null ? null : session.reservationId().value(),
                ReceiptResponse.from(result.receipt()));
    }

    public record SpaceResponse(
            int floorNumber,
            String zoneCode,
            int spaceNumber) {
    }

    public record ReceiptResponse(
            UUID receiptId,
            UUID ratePlanId,
            long ratePlanVersion,
            String billableDuration,
            long billingIncrements,
            long grossChargeMinor,
            long capDiscountMinor,
            long totalMinor,
            String currency,
            Instant issuedAt) {

        private static ReceiptResponse from(ParkingReceipt receipt) {
            return new ReceiptResponse(
                    receipt.id(),
                    receipt.quote().ratePlanId().value(),
                    receipt.quote().ratePlanVersion(),
                    receipt.quote().billableDuration().toString(),
                    receipt.quote().billingIncrements(),
                    receipt.quote().grossCharge().minorUnits(),
                    receipt.quote().capDiscount().minorUnits(),
                    receipt.quote().total().minorUnits(),
                    receipt.quote().total().currency().getCurrencyCode(),
                    receipt.issuedAt());
        }
    }
}
