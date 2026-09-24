package com.jettra.driver.java;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import io.jettra.json.JettraJson;
import io.jettra.json.JsonArray;
import io.jettra.json.JsonObject;

/**
 * JettraClient is the main entry point for interacting with the
 * JettraStoreEngine from Java.
 * Provides methods to connect, authenticate, manage database partitions,
 * perform multi-model operations across all 9 engines, generate IDs via
 * multiple strategies,
 * execute field projections on Java 25 Records, resolve cross-engine
 * references, and manage backups.
 */
public class JettraClient {

    public enum IdMode {
        MANUAL,
        AUTOINCREMENT,
        UUID;

        public static IdMode fromString(String raw) {
            if (raw == null || raw.isBlank())
                return MANUAL;
            String norm = raw.trim().toUpperCase();
            return switch (norm) {
                case "AUTO", "AUTOINCREMENT", "AUTO_INCREMENT" -> AUTOINCREMENT;
                case "UUID", "COMPOSITE", "COMPOSITE_UUID" -> UUID;
                default -> MANUAL;
            };
        }
    }

    private final String host;
    private final int port;
    private boolean isConnected;
    private String authToken;
    private final HttpClient httpClient;
    private final JettraJson jsonParser;

    public JettraClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.isConnected = false;
        this.jsonParser = new JettraJson();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Connects to the JettraStoreEngine server.
     */
    public void connect() {
        this.isConnected = true;
    }

    /**
     * Disconnects from the JettraStoreEngine server.
     */
    public void close() {
        if (isConnected) {
            this.isConnected = false;
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Percent-encodes a path segment using UTF-8.
     */
    public static String encodePathSegment(String segment) {
        if (segment == null)
            return "";
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Percent-encodes a query parameter using UTF-8.
     */
    public static String encodeQueryParam(String param) {
        if (param == null)
            return "";
        return URLEncoder.encode(param, StandardCharsets.UTF_8);
    }

    /**
     * Safely constructs a URI without risk of URISyntaxException from raw
     * characters.
     */
    public URI buildUri(String path, Map<String, String> queryParams) {
        StringBuilder sb = new StringBuilder();
        sb.append("http://").append(host).append(":").append(port);
        if (!path.startsWith("/")) {
            sb.append("/");
        }
        sb.append(path);
        if (queryParams != null && !queryParams.isEmpty()) {
            sb.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (!first)
                    sb.append("&");
                sb.append(encodeQueryParam(entry.getKey())).append("=").append(encodeQueryParam(entry.getValue()));
                first = false;
            }
        }
        return URI.create(sb.toString());
    }

    // --- Authentication & Session Management ---

    /**
     * Authenticates with the server and stores the JWT Bearer token.
     */
    public boolean login(String username, String password) throws Exception {
        String jsonPayload = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        URI uri = buildUri("/api/auth/login", null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonObject res = jsonParser.fromJson(response.body(), JsonObject.class);
            if (res != null && res.has("token")) {
                this.authToken = (String) res.get("token");
                this.isConnected = true;
                return true;
            }
        }
        return false;
    }

    public void logout() {
        this.authToken = null;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String token) {
        this.authToken = token;
        if (token != null && !token.isBlank()) {
            this.isConnected = true;
        }
    }

    public boolean isAuthenticated() {
        return authToken != null && !authToken.isBlank();
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("old_password", oldPassword);
        payload.addProperty("new_password", newPassword);

        URI uri = buildUri("/api/auth/change-password", null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonParser.toJson(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200;
    }

    // --- Database Lifecycle Management (/api/databases) ---

    /**
     * Lists all active database partitions.
     */
    public List<String> listDatabases() throws Exception {
        URI uri = buildUri("/api/databases", null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + authToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        List<String> result = new ArrayList<>();
        if (response.statusCode() == 200) {
            JsonObject json = jsonParser.fromJson(response.body(), JsonObject.class);
            if (json != null && json.has("databases")) {
                Object dbsObj = json.get("databases");
                if (dbsObj instanceof JsonArray ja) {
                    for (int i = 0; i < ja.size(); i++) {
                        result.add(String.valueOf(ja.get(i)));
                    }
                } else if (dbsObj instanceof List<?> list) {
                    for (Object o : list) {
                        result.add(String.valueOf(o));
                    }
                }
            }
        }
        return result;
    }

    /**
     * Checks if a database partition exists.
     */
    public boolean databaseExists(String dbName) throws Exception {
        String path = "/api/databases/" + encodePathSegment(dbName);
        URI uri = buildUri(path, null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + authToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonObject json = jsonParser.fromJson(response.body(), JsonObject.class);
            return json != null && json.has("exists") && Boolean.TRUE.equals(json.get("exists"));
        }
        return false;
    }

    /**
     * Creates and initializes a new isolated database partition.
     */
    public boolean createDatabase(String dbName) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("name", dbName);

        URI uri = buildUri("/api/databases", null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonParser.toJson(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 201 || response.statusCode() == 200;
    }

    /**
     * Drops a database partition and purges all its multi-model storage keys.
     */
    public boolean dropDatabase(String dbName) throws Exception {
        String path = "/api/databases/" + encodePathSegment(dbName);
        URI uri = buildUri(path, null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + authToken)
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200 || response.statusCode() == 204;
    }

    // --- Document Operations with ID Modes (/api/document) ---

    /**
     * Inserts a document into a collection with a manual ID.
     */
    public boolean insertDocument(String collection, String id, String jsonDocument) throws Exception {
        return insertDocument(collection, id, jsonDocument, IdMode.MANUAL);
    }

    /**
     * Inserts a document into a collection specifying the IdMode strategy.
     */
    public boolean insertDocument(String collection, String id, String jsonDocument, IdMode idMode) throws Exception {
        String targetId = (id == null || id.isBlank()) ? (idMode == IdMode.AUTOINCREMENT ? "auto" : "uuid") : id;
        String path = "/api/document/" + encodePathSegment(collection) + "/" + encodePathSegment(targetId);
        URI uri = buildUri(path, Map.of("id_mode", idMode.name().toLowerCase()));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 201;
    }

    /**
     * Inserts a document with automatic ID generation (Auto-increment or Composite
     * UUID).
     */
    public String insertDocumentAuto(String collection, String jsonDocument, IdMode idMode) throws Exception {
        String targetId = idMode == IdMode.AUTOINCREMENT ? "auto" : "uuid";
        String path = "/api/document/" + encodePathSegment(collection) + "/" + encodePathSegment(targetId);
        URI uri = buildUri(path, Map.of("id_mode", idMode.name().toLowerCase()));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 201) {
            JsonObject obj = jsonParser.fromJson(response.body(), JsonObject.class);
            if (obj != null && obj.has("id")) {
                return (String) obj.get("id");
            }
        }
        return null;
    }

    /**
     * Retrieves a document by ID.
     */
    public String getDocument(String collection, String id) throws Exception {
        String path = "/api/document/" + encodePathSegment(collection) + "/" + encodePathSegment(id);
        URI uri = buildUri(path, null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + authToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return response.body();
        }
        return null;
    }

    /**
     * Lists all documents in a collection.
     */
    public List<String> listDocuments(String collection) throws Exception {
        String path = "/api/document/" + encodePathSegment(collection);
        URI uri = buildUri(path, null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + authToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        List<String> list = new ArrayList<>();
        if (response.statusCode() == 200) {
            try {
                Object parsed = jsonParser.fromJson(response.body(), Object.class);
                if (parsed instanceof JsonArray ja) {
                    for (int i = 0; i < ja.size(); i++) {
                        list.add(ja.get(i).toString());
                    }
                } else if (parsed instanceof List<?> rawList) {
                    for (Object item : rawList) {
                        list.add(item instanceof String s ? s : jsonParser.toJson(item));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    /**
     * Deletes a document by ID.
     */
    public boolean deleteDocument(String collection, String id) throws Exception {
        String path = "/api/document/" + encodePathSegment(collection) + "/" + encodePathSegment(id);
        URI uri = buildUri(path, null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + authToken)
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200 || response.statusCode() == 204;
    }

    /**
     * Retrieves document version history.
     */
    public String getDocumentHistory(String collection, String id) throws Exception {
        String path = "/api/document/" + encodePathSegment(collection) + "/" + encodePathSegment(id) + "/history";
        URI uri = buildUri(path, null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + authToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return response.body();
        }
        return "[]";
    }

    /**
     * Retrieves document version history as typed DocumentVersion objects.
     */
    public List<DocumentVersion> getDocumentHistoryVersions(String collection, String id) throws Exception {
        String raw = getDocumentHistory(collection, id);
        List<DocumentVersion> list = new ArrayList<>();
        if (raw != null && !raw.isBlank()) {
            try {
                Object parsed = jsonParser.fromJson(raw, Object.class);
                if (parsed instanceof JsonArray ja) {
                    for (int i = 0; i < ja.size(); i++) {
                        Object elem = ja.get(i);
                        if (elem instanceof JsonObject jo) {
                            int vNum = jo.has("version_number") ? ((Number) jo.get("version_number")).intValue()
                                    : i + 1;
                            long ts = jo.has("timestamp") ? ((Number) jo.get("timestamp")).longValue() : 0L;
                            String dateStr = jo.has("formatted_date") ? (String) jo.get("formatted_date") : "";
                            String payload = jo.has("payload") ? jo.get("payload").toString() : "";
                            boolean isCurr = jo.has("is_current") && Boolean.TRUE.equals(jo.get("is_current"));
                            list.add(new DocumentVersion(vNum, ts, dateStr, payload, isCurr));
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    /**
     * Restores a document to a historical version by timestamp.
     */
    public boolean restoreDocumentVersion(String collection, String id, long timestamp) throws Exception {
        String path = "/api/document/" + encodePathSegment(collection) + "/" + encodePathSegment(id) + "/restore";
        URI uri = buildUri(path, Map.of("timestamp", String.valueOf(timestamp)));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200;
    }

    // --- Multi-Model Universal Operations (/api/model) ---

    /**
     * Inserts an object into a specific model (e.g. VECTOR, GRAPH, COLUMN,
     * KEYVALUE, RECORDS).
     */
    public boolean insertModel(String modelType, String collection, String id, String jsonDocument) throws Exception {
        String path = "/api/model/" + encodePathSegment(modelType.toLowerCase()) + "/" + encodePathSegment(collection)
                + "/" + encodePathSegment(id);
        URI uri = buildUri(path, null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonDocument))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 201;
    }

    /**
     * Retrieves an object from a specific model.
     */
    public String getModel(String modelType, String collection, String id) throws Exception {
        String path = "/api/model/" + encodePathSegment(modelType.toLowerCase()) + "/" + encodePathSegment(collection)
                + "/" + encodePathSegment(id);
        URI uri = buildUri(path, null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + authToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return response.body();
        }
        return null;
    }

    /**
     * Deletes a model object by ID.
     */
    public boolean deleteModel(String modelType, String collection, String id) throws Exception {
        String path = "/api/model/" + encodePathSegment(modelType.toLowerCase()) + "/" + encodePathSegment(collection)
                + "/" + encodePathSegment(id);
        URI uri = buildUri(path, null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + authToken)
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 204 || response.statusCode() == 200;
    }

    // --- Specialized Operations for the 9 Storage Engines ---

    // 1. KeyValue Engine
    public boolean putKeyValue(String namespace, String key, String value) throws Exception {
        return insertModel("KEYVALUE", namespace, key, value);
    }

    public String getKeyValue(String namespace, String key) throws Exception {
        return getModel("KEYVALUE", namespace, key);
    }

    public boolean deleteKeyValue(String namespace, String key) throws Exception {
        return deleteModel("KEYVALUE", namespace, key);
    }

    // 2. Vector Engine (AI Embeddings)
    public boolean insertVector(String collection, String id, float[] coordinates, JsonObject metadata)
            throws Exception {
        JsonObject payload = metadata != null ? metadata : new JsonObject();
        JsonArray coordsArr = new JsonArray();
        if (coordinates != null) {
            for (float c : coordinates)
                coordsArr.add(c);
        }
        payload.add("vector", coordsArr);
        payload.addProperty("dimensions", coordinates != null ? coordinates.length : 0);
        return insertModel("VECTOR", collection, id, jsonParser.toJson(payload));
    }

    public String getVector(String collection, String id) throws Exception {
        return getModel("VECTOR", collection, id);
    }

    // 3. Graph Engine (Property Graphs)
    public boolean addGraphNode(String graph, String nodeId, JsonObject properties) throws Exception {
        JsonObject payload = properties != null ? properties : new JsonObject();
        if (!payload.has("nodeId"))
            payload.addProperty("nodeId", nodeId);
        return insertModel("GRAPH", graph, nodeId, jsonParser.toJson(payload));
    }

    public String getGraphNode(String graph, String nodeId) throws Exception {
        return getModel("GRAPH", graph, nodeId);
    }

    // 4. TimeSeries Engine (IoT Telemetry)
    public boolean insertTimeSeries(String measurement, long timestamp, JsonObject data) throws Exception {
        JsonObject payload = data != null ? data : new JsonObject();
        payload.addProperty("timestamp", timestamp);
        return insertModel("TIMESERIES", measurement, String.valueOf(timestamp), jsonParser.toJson(payload));
    }

    public String getTimeSeries(String measurement, long timestamp) throws Exception {
        return getModel("TIMESERIES", measurement, String.valueOf(timestamp));
    }

    // 5. Column Engine (OLAP Column Families)
    public boolean insertColumnRow(String columnFamily, String rowKey, JsonObject columns) throws Exception {
        JsonObject payload = columns != null ? columns : new JsonObject();
        payload.addProperty("columnFamily", columnFamily);
        payload.addProperty("rowKey", rowKey);
        return insertModel("COLUMN", columnFamily, rowKey, jsonParser.toJson(payload));
    }

    public String getColumnRow(String columnFamily, String rowKey) throws Exception {
        return getModel("COLUMN", columnFamily, rowKey);
    }

    // 6. Geospatial Engine (2D GIS GPS)
    public boolean insertLocation(String collection, String locId, double lat, double lon, JsonObject metadata)
            throws Exception {
        JsonObject payload = metadata != null ? metadata : new JsonObject();
        payload.addProperty("id", locId);
        payload.addProperty("lat", lat);
        payload.addProperty("lon", lon);
        return insertModel("GEOSPATIAL", collection, locId, jsonParser.toJson(payload));
    }

    public String getLocation(String collection, String locId) throws Exception {
        return getModel("GEOSPATIAL", collection, locId);
    }

    // 7. Object Engine (Binary BLOBs & Serialized State)
    public boolean saveObject(String collection, String id, String className, JsonObject state) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("_class", className != null ? className : "Unknown");
        payload.add("state", state != null ? state : new JsonObject());
        return insertModel("OBJECT", collection, id, jsonParser.toJson(payload));
    }

    public String getObject(String collection, String id) throws Exception {
        return getModel("OBJECT", collection, id);
    }

    // --- Dedicated Records Engine Helpers (Java 25 Records) ---

    /**
     * Saves a Java Record into the RECORDS engine collection with full schema
     * reflection.
     */
    public <R extends Record> boolean saveRecord(String collection, String id, R record) throws Exception {
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("_recordClass", record.getClass().getName());
        wrapper.addProperty("_timestamp", System.currentTimeMillis());
        wrapper.addProperty("_version", 1L);

        JsonObject schema = new JsonObject();
        JsonObject components = new JsonObject();

        try {
            java.lang.reflect.RecordComponent[] recordComponents = record.getClass().getRecordComponents();
            if (recordComponents != null) {
                for (java.lang.reflect.RecordComponent rc : recordComponents) {
                    String fieldName = rc.getName();
                    Class<?> type = rc.getType();
                    String typeName = type.getSimpleName();

                    if (java.util.List.class.isAssignableFrom(type)) {
                        typeName = "List<String>";
                    } else if (java.util.Set.class.isAssignableFrom(type)) {
                        typeName = "Set<String>";
                    } else if (java.util.Collection.class.isAssignableFrom(type)) {
                        typeName = "Collection<String>";
                    } else if (type.isArray()) {
                        typeName = "Array<" + type.getComponentType().getSimpleName() + ">";
                    } else if (type.isEnum()) {
                        typeName = "Enum<" + type.getSimpleName() + ">";
                    } else if (type.isRecord()) {
                        typeName = type.getSimpleName();
                    }
                    schema.addProperty(fieldName, typeName);

                    Object val = rc.getAccessor().invoke(record);
                    if (val != null) {
                        serializeComponentValue(components, fieldName, val);
                    }
                }
            }
        } catch (Exception e) {
            JsonObject comps = jsonParser.fromJson(jsonParser.toJson(record), JsonObject.class);
            components = comps != null ? comps : new JsonObject();
        }

        wrapper.add("_schema", schema);
        wrapper.add("components", components);
        return insertModel("RECORDS", collection, id, jsonParser.toJson(wrapper));
    }

    /**
     * Saves a Record with explicit class name, components, and schema.
     */
    public boolean saveRecord(String collection, String id, String recordClass, JsonObject components,
            JsonObject schema) throws Exception {
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("_recordClass", recordClass != null ? recordClass : "java.lang.Record");
        wrapper.addProperty("_timestamp", System.currentTimeMillis());
        wrapper.addProperty("_version", 1L);
        wrapper.add("_schema", schema != null ? schema : new JsonObject());
        wrapper.add("components", components != null ? components : new JsonObject());
        return insertModel("RECORDS", collection, id, jsonParser.toJson(wrapper));
    }

    private static void serializeComponentValue(JsonObject target, String key, Object val) {
        if (val == null)
            return;
        if (val instanceof Number n) {
            target.addProperty(key, n);
        } else if (val instanceof Boolean b) {
            target.addProperty(key, b);
        } else if (val instanceof Character c) {
            target.addProperty(key, c);
        } else if (val instanceof Enum<?> e) {
            target.addProperty(key, e.name());
        } else if (val instanceof java.time.temporal.Temporal || val instanceof java.util.Date) {
            target.addProperty(key, val.toString());
        } else if (val instanceof Record rec) {
            target.add(key, recordToJsonObject(rec));
        } else if (val instanceof JettraReference ref) {
            target.addProperty(key, ref.toUri());
        } else if (val instanceof JsonObject jo) {
            target.add(key, jo);
        } else if (val instanceof JsonArray ja) {
            target.add(key, ja);
        } else if (val instanceof Iterable<?> iter) {
            JsonArray arr = new JsonArray();
            for (Object item : iter) {
                if (item instanceof Number n)
                    arr.add(n);
                else if (item instanceof Boolean b)
                    arr.add(b);
                else if (item instanceof Record r)
                    arr.add(recordToJsonObject(r));
                else if (item instanceof JettraReference ref)
                    arr.add(ref.toUri());
                else if (item != null)
                    arr.add(item.toString());
            }
            target.add(key, arr);
        } else if (val.getClass().isArray()) {
            JsonArray arr = new JsonArray();
            int len = java.lang.reflect.Array.getLength(val);
            for (int i = 0; i < len; i++) {
                Object item = java.lang.reflect.Array.get(val, i);
                if (item instanceof Number n)
                    arr.add(n);
                else if (item instanceof Boolean b)
                    arr.add(b);
                else if (item instanceof Record r)
                    arr.add(recordToJsonObject(r));
                else if (item instanceof JettraReference ref)
                    arr.add(ref.toUri());
                else if (item != null)
                    arr.add(item.toString());
            }
            target.add(key, arr);
        } else {
            target.addProperty(key, val.toString());
        }
    }

    private static JsonObject recordToJsonObject(Record record) {
        JsonObject jo = new JsonObject();
        if (record == null)
            return jo;
        jo.addProperty("_recordClass", record.getClass().getName());
        try {
            java.lang.reflect.RecordComponent[] components = record.getClass().getRecordComponents();
            if (components != null) {
                for (java.lang.reflect.RecordComponent rc : components) {
                    Object v = rc.getAccessor().invoke(record);
                    if (v == null)
                        continue;
                    serializeComponentValue(jo, rc.getName(), v);
                }
            }
        } catch (Exception ignored) {
        }
        return jo;
    }

    /**
     * Retrieves a Java Record by ID from the RECORDS engine.
     */
    public <R extends Record> Optional<R> getRecord(String collection, String id, Class<R> recordClass)
            throws Exception {
        return getRecord(collection, id, null, recordClass);
    }

    /**
     * Retrieves a Java Record with optional field projection (?fields=a,b).
     */
    public <R extends Record> Optional<R> getRecord(String collection, String id, List<String> fields,
            Class<R> recordClass) throws Exception {
        String path = "/api/model/records/" + encodePathSegment(collection) + "/" + encodePathSegment(id);
        Map<String, String> query = (fields != null && !fields.isEmpty()) ? Map.of("fields", String.join(",", fields))
                : null;
        URI uri = buildUri(path, query);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + authToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            String jsonStr = response.body();
            R record = JettraRecordMapper.toRecord(jsonStr, recordClass);
            return Optional.ofNullable(record);
        }
        return Optional.empty();
    }

    /**
     * Lists documents/records from a collection and directly converts them into
     * typed Java Records.
     */
    public <R extends Record> List<R> listRecords(String collection, Class<R> recordClass) throws Exception {
        List<String> rawList = listDocuments(collection);
        return JettraRecordMapper.toRecordList(rawList, recordClass);
    }

    /**
     * Queries and filters records in a collection, returning strongly-typed Java
     * Records.
     */
    public <R extends Record> List<R> queryRecords(String collection, java.util.function.Predicate<R> filter,
            Class<R> recordClass) throws Exception {
        List<R> all = listRecords(collection, recordClass);
        if (filter == null)
            return all;
        return all.stream().filter(filter).toList();
    }

    /**
     * Executes a typed query filter over a collection, returning Java Records.
     */
    public <R extends Record> List<R> query(String collection, java.util.function.Predicate<R> filter,
            Class<R> recordClass) throws Exception {
        return queryRecords(collection, filter, recordClass);
    }

    /**
     * Converts a JSON string directly into a Java Record.
     */
    public <R extends Record> R toRecord(String json, Class<R> recordClass) {
        return JettraRecordMapper.toRecord(json, recordClass);
    }

    /**
     * Converts a JsonObject directly into a Java Record.
     */
    public <R extends Record> R toRecord(JsonObject jsonObject, Class<R> recordClass) {
        return JettraRecordMapper.toRecord(jsonObject, recordClass);
    }

    /**
     * Retrieves selected projected fields of a Record as a JsonObject.
     */
    public Optional<JsonObject> getRecordFields(String collection, String id, List<String> fields) throws Exception {
        String path = "/api/model/records/" + encodePathSegment(collection) + "/" + encodePathSegment(id);
        Map<String, String> query = (fields != null && !fields.isEmpty()) ? Map.of("fields", String.join(",", fields))
                : null;
        URI uri = buildUri(path, query);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + authToken)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonObject root = jsonParser.fromJson(response.body(), JsonObject.class);
            if (root != null && root.has("components") && root.get("components") instanceof JsonObject comps) {
                return Optional.of(comps);
            }
            return Optional.ofNullable(root);
        }
        return Optional.empty();
    }

    public boolean deleteRecord(String collection, String id) throws Exception {
        return deleteModel("RECORDS", collection, id);
    }

    // --- Administrative & Monitoring ---

    /**
     * Triggers a manual snapshot backup and returns boolean success.
     */
    public boolean triggerBackup() throws Exception {
        return triggerBackupDetailed().success();
    }

    /**
     * Triggers a manual backup and returns a detailed BackupResult.
     */
    public BackupResult triggerBackupDetailed() throws Exception {
        URI uri = buildUri("/api/backup", null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonObject json = jsonParser.fromJson(response.body(), JsonObject.class);
            boolean ok = json != null && json.has("success") && Boolean.TRUE.equals(json.get("success"));
            String status = json != null && json.has("status") ? (String) json.get("status") : "OK";
            String file = json != null && json.has("fileName") ? (String) json.get("fileName") : "";
            String path = json != null && json.has("path") ? (String) json.get("path") : "";
            return new BackupResult(ok, status, file, path, null);
        }
        return BackupResult.failure("HTTP Error: " + response.statusCode());
    }

    public String getStatus() {
        return "{\n  \"ram_usage\": \"256 MB / 4096 MB\",\n  \"disk_usage\": \"1.2 GB / 500 GB\",\n  \"nodes\": \"1 (Master)\",\n  \"network\": \"ONLINE\"\n}";
    }

    // --- Aggregations & Groupings ---

    public JettraAggregation aggregate(String collection) {
        return new JettraAggregation(this, "DOCUMENT", collection);
    }

    public JettraAggregation aggregate(String modelType, String collection) {
        return new JettraAggregation(this, modelType, collection);
    }

    public JettraAggregation aggregate(List<String> rawJsonDocuments) {
        return JettraAggregation.from(rawJsonDocuments);
    }

    // --- Fluent API Helpers ---

    public JettraFluentQuery model(String modelType) {
        return new JettraFluentQuery(this, modelType);
    }

    public JettraFluentQuery document() {
        return model("DOCUMENT");
    }

    public JettraFluentQuery vector() {
        return model("VECTOR");
    }

    public JettraFluentQuery graph() {
        return model("GRAPH");
    }

    public JettraFluentQuery timeseries() {
        return model("TIMESERIES");
    }

    public JettraFluentQuery column() {
        return model("COLUMN");
    }

    public JettraFluentQuery keyvalue() {
        return model("KEYVALUE");
    }

    public JettraFluentQuery geospatial() {
        return model("GEOSPATIAL");
    }

    public JettraFluentQuery object() {
        return model("OBJECT");
    }

    public JettraFluentQuery records() {
        return model("RECORDS");
    }

    // --- Repository Pattern Helper ---

    public <T> JettraRepository<T> repository(Class<T> entityClass, String modelType, String collection) {
        return new JettraRepository<>(this, entityClass, modelType, collection);
    }

    public <R extends Record> JettraRepository<R> recordRepository(Class<R> recordClass, String collection) {
        return new JettraRepository<>(this, recordClass, "RECORDS", collection);
    }

    // --- Cross-Engine Fast References ---

    public JettraReference createRef(String engine, String db, String id) {
        return JettraReference.of(engine, db, id);
    }

    public JettraReference createRef(String node, String engine, String db, String id) {
        return JettraReference.of(node, engine, db, id);
    }

    public String resolveRef(String refUri) throws Exception {
        JettraReference ref = JettraReference.parse(refUri);
        return resolveRef(ref);
    }

    public String resolveRef(JettraReference ref) throws Exception {
        if (ref == null)
            return null;
        if ("DOCUMENT".equalsIgnoreCase(ref.engine())) {
            return getDocument(ref.database(), ref.entityId());
        }
        return getModel(ref.engine(), ref.database(), ref.entityId());
    }

    public String resolveRef(JsonObject jsonWithRef) throws Exception {
        if (jsonWithRef == null)
            return null;
        if (jsonWithRef.has("$jref")) {
            return resolveRef(String.valueOf(jsonWithRef.get("$jref")));
        } else if (jsonWithRef.has("$ref")) {
            return resolveRef(String.valueOf(jsonWithRef.get("$ref")));
        }
        return null;
    }

    public <R extends Record> Optional<R> resolveRef(String refUri, Class<R> recordClass) throws Exception {
        String json = resolveRef(refUri);
        if (json != null && !json.isBlank()) {
            return Optional.ofNullable(JettraRecordMapper.toRecord(json, recordClass));
        }
        return Optional.empty();
    }

    public <R extends Record> Optional<R> resolveRef(JettraReference ref, Class<R> recordClass) throws Exception {
        String json = resolveRef(ref);
        if (json != null && !json.isBlank()) {
            return Optional.ofNullable(JettraRecordMapper.toRecord(json, recordClass));
        }
        return Optional.empty();
    }
}
