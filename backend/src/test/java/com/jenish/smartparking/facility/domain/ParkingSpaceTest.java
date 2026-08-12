package com.jenish.smartparking.facility.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ParkingSpaceTest {

    @Test
    void acceptsVehiclesAtOrBelowTheSpaceSize() {
        ParkingSpace mediumSpace = space(SizeClass.MEDIUM);

        assertTrue(mediumSpace.canAccept(SizeClass.SMALL));
        assertTrue(mediumSpace.canAccept(SizeClass.MEDIUM));
        assertFalse(mediumSpace.canAccept(SizeClass.LARGE));
    }

    @Test
    void excludesAnOutOfServiceSpaceFromCompatibility() {
        ParkingSpace active = space(SizeClass.LARGE);
        ParkingSpace outOfService = active.takeOutOfService();

        assertFalse(outOfService.canAccept(SizeClass.SMALL));
        assertTrue(outOfService.returnToService().canAccept(SizeClass.LARGE));
        assertSame(outOfService, outOfService.takeOutOfService());
    }

    private static ParkingSpace space(SizeClass sizeClass) {
        return new ParkingSpace(new SpaceNumber(1), sizeClass, SpaceOperationalState.ACTIVE);
    }
}
