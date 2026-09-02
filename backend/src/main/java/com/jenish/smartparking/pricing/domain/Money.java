package com.jenish.smartparking.pricing.domain;

import java.util.Currency;
import java.util.Objects;

public record Money(long minorUnits, Currency currency) {

    public Money {
        if (minorUnits < 0) {
            throw new IllegalArgumentException("minorUnits must not be negative");
        }
        Objects.requireNonNull(currency, "currency must not be null");
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        if (other.minorUnits > minorUnits) {
            throw new IllegalArgumentException("result must not be negative");
        }
        return new Money(minorUnits - other.minorUnits, currency);
    }

    public Money multiply(long factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("factor must not be negative");
        }
        return new Money(Math.multiplyExact(minorUnits, factor), currency);
    }

    public Money min(Money other) {
        requireSameCurrency(other);
        return minorUnits <= other.minorUnits ? this : other;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("currencies must match");
        }
    }
}
