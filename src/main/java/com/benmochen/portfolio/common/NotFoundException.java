package com.benmochen.portfolio.common;

/**
 * Thrown when a requested resource does not exist.
 * Translated to HTTP 404 by GlobalExceptionHandler.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String what, Object id) {
        return new NotFoundException(what + " not found: " + id);
    }
}
