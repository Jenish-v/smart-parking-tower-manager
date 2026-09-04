package com.jenish.smartparking.pricing.application;

import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.facility.domain.FacilityId;
import com.jenish.smartparking.pricing.domain.AdjustmentReason;
import com.jenish.smartparking.pricing.domain.ParkingReceipt;
import com.jenish.smartparking.pricing.domain.ReceiptStatement;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PricingService {

    ParkingReceipt assess(
            UUID sessionId,
            SizeClass sizeClass,
            Instant enteredAt,
            Instant exitedAt);

    Optional<ParkingReceipt> findReceipt(UUID sessionId);

    Map<UUID, ParkingReceipt> findReceipts(Collection<UUID> sessionIds);

    ReceiptStatement findStatement(FacilityId facilityId, UUID sessionId);

    ReceiptStatement adjust(
            FacilityId facilityId,
            UUID sessionId,
            UUID adjustmentId,
            long amountMinor,
            AdjustmentReason reason,
            String reasonDetail,
            String operatorReference);
}
