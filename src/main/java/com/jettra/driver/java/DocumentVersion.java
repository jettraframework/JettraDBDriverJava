package com.jettra.driver.java;

/**
 * Represents a historical revision of a document in JettraStoreEngine (MVCC).
 */
public record DocumentVersion(
        int versionNumber,
        long timestamp,
        String formattedDate,
        String payload,
        boolean isCurrent) {
}
