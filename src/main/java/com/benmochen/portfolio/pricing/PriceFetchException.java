package com.benmochen.portfolio.pricing;

/** Thrown when market data cannot be retrieved. Translated to HTTP 400. */
public class PriceFetchException extends RuntimeException {

    public PriceFetchException(String message) {
        super(message);
    }
}
