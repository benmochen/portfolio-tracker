package com.benmochen.portfolio.importer;

/**
 * Thrown when an uploaded file cannot be read or interpreted.
 * Translated to HTTP 400 by GlobalExceptionHandler.
 */
public class ImportException extends RuntimeException {

    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
