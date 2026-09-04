package com.jenish.smartparking.pricing.application;

public final class NegativeAdjustedTotalException extends RuntimeException {

    public NegativeAdjustedTotalException() {
        super("An adjustment cannot reduce the receipt total below zero");
    }
}
