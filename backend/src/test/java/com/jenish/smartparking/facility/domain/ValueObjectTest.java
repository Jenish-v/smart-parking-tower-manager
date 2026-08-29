package com.jenish.smartparking.facility.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ValueObjectTest {

    @Test
    void normalizesZoneCodes() {
        assertEquals("A1", new ZoneCode(" a1 ").value());
    }

    @Test
    void rejectsInvalidIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> new FloorNumber(0));
        assertThrows(IllegalArgumentException.class, () -> new SpaceNumber(-1));
        assertThrows(IllegalArgumentException.class, () -> new ZoneCode("A-1"));
    }
}
