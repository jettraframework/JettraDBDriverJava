package com.jettra.driver.java;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JettraReference represents a direct, ultra-fast O(1) cross-engine pointer in Java.
 * URI Syntax: jref://[node@][ENGINE:]database/entityId
 */
public record JettraReference(
    String node,
    String engine,
    String database,
    String entityId,
    String directStorageKey
) {

    private static final Pattern JREF_PATTERN = Pattern.compile(
        "^jref://(?:([a-zA-Z0-9_-]+)@)?(?:([A-Z]+):)?([a-zA-Z0-9_]+)/([a-zA-Z0-9_.:-]+)$"
    );

    public static JettraReference of(String engine, String database, String entityId) {
        return of(null, engine, database, entityId);
    }

    public static JettraReference of(String node, String engine, String database, String entityId) {
        String normEngine = (engine != null && !engine.isBlank()) ? engine.toUpperCase() : "DOCUMENT";
        String normDb = database != null ? database.trim() : "default";
        String normId = entityId != null ? entityId.trim() : "";
        String normNode = (node != null && !node.isBlank()) ? node.trim() : null;
        String directKey = computeDirectStorageKey(normEngine, normDb, normId);
        return new JettraReference(normNode, normEngine, normDb, normId, directKey);
    }

    public static JettraReference parse(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("Reference URI cannot be null or empty");
        }
        String clean = uri.trim();
        Matcher m = JREF_PATTERN.matcher(clean);
        if (m.matches()) {
            String node = m.group(1);
            String engine = m.group(2) != null ? m.group(2) : "DOCUMENT";
            String db = m.group(3);
            String id = m.group(4);
            return of(node, engine, db, id);
        }

        if (clean.contains(":")) {
            String[] parts = clean.split(":");
            if (parts.length == 3) {
                return of(null, parts[0], parts[1], parts[2]);
            } else if (parts.length == 2) {
                return of(null, "DOCUMENT", parts[0], parts[1]);
            }
        }
        throw new IllegalArgumentException("Invalid JettraReference URI format: " + uri);
    }

    public static boolean isReference(String str) {
        return str != null && (str.startsWith("jref://") || str.startsWith("{\"$jref\""));
    }

    public static String computeDirectStorageKey(String engine, String database, String entityId) {
        String pfx = switch (engine != null ? engine.toUpperCase() : "DOCUMENT") {
            case "RECORDS" -> "rec:";
            case "KEYVALUE" -> "kv:";
            case "VECTOR" -> "vec:";
            case "GRAPH" -> "graph:";
            case "TIMESERIES" -> "ts:";
            case "COLUMN" -> "col:";
            case "GEOSPATIAL" -> "geo:";
            case "OBJECT" -> "obj:";
            default -> ""; // DOCUMENT
        };
        return pfx + database + ":" + entityId;
    }

    public String toUri() {
        StringBuilder sb = new StringBuilder("jref://");
        if (node != null && !node.isBlank()) {
            sb.append(node).append("@");
        }
        if (engine != null && !"DOCUMENT".equalsIgnoreCase(engine)) {
            sb.append(engine.toUpperCase()).append(":");
        }
        sb.append(database).append("/").append(entityId);
        return sb.toString();
    }

    @Override
    public String toString() {
        return toUri();
    }
}
