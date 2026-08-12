package com.benmochen.portfolio.common;

/**
 * Thrown when a request violates a uniqueness or state rule, e.g. creating an
 * account whose externalId already exists.
 * Translated to HTTP 409 by GlobalExceptionHandler.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
