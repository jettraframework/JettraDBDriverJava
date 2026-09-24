package com.jettra.driver.java;

/**
 * Standard runtime exception for JettraStoreEngine Java Driver operations.
 */
public class JettraDriverException extends RuntimeException {

    private final int statusCode;

    public JettraDriverException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public JettraDriverException(String message, int statusCode) {
        super(message + " (HTTP " + statusCode + ")");
        this.statusCode = statusCode;
    }

    public JettraDriverException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
