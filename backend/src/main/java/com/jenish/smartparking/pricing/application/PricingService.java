package com.jenish.smartparking.pricing.application;

import com.jenish.smartparking.facility.domain.SizeClass;
import com.jenish.smartparking.pricing.domain.ParkingReceipt;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PricingService {

    ParkingReceipt assess(
            UUID sessionId,
            SizeClass sizeClass,
            Instant enteredAt,
            Instant exitedAt);

    Optional<ParkingReceipt> findReceipt(UUID sessionId);
}
