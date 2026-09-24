package com.jettra.driver.java;

/**
 * Result details returned from a manual or automated backup execution.
 */
public record BackupResult(
        boolean success,
        String status,
        String fileName,
        String path,
        String errorMessage) {
    public static BackupResult success(String status, String fileName, String path) {
        return new BackupResult(true, status, fileName, path, null);
    }

    public static BackupResult failure(String errorMessage) {
        return new BackupResult(false, "FAILED", null, null, errorMessage);
    }
}
