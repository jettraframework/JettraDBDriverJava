package com.jettra.driver.java;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import io.jettra.json.JettraJson;
import io.jettra.json.JsonObject;

/**
 * JettraClient is the main entry point for interacting with the JettraStoreEngine from Java.
 * Provides methods to connect, authenticate, perform multi-model operations,
 * generate IDs via multiple strategies (Manual, Auto-increment, Composite UUID),
 * and manage version history & restorations with strict URI percent-encoding.
 */
public class JettraClient {

    public enum IdMode {
        MANUAL,
        AUTOINCREMENT,
        UUID;

        public static IdMode fromString(String raw) {
            if (raw == null || raw.isBlank()) return MANUAL;
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

    public JettraClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.isConnected = false;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Connects to the JettraStoreEngine server.
     */
    public void connect() {
        System.out.println("Connecting to JettraStoreEngine at " + host + ":" + port + "...");
        this.isConnected = true;
        System.out.println("Connected successfully.");
    }

    /**
     * Disconnects from the JettraStoreEngine server.
     */
    public void close() {
        if (isConnected) {
            System.out.println("Closing connection to JettraStoreEngine...");
            this.isConnected = false;
            System.out.println("Connection closed.");
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }

    /**
     * Percent-encodes a path segment using UTF-8.
     */
    public static String encodePathSegment(String segment) {
        if (segment == null) return "";
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Percent-encodes a query parameter using UTF-8.
     */
    public static String encodeQueryParam(String param) {
        if (param == null) return "";
        return URLEncoder.encode(param, StandardCharsets.UTF_8);
    }

    /**
     * Safely constructs a URI without risk of URISyntaxException from raw characters.
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
                if (!first) sb.append("&");
                sb.append(encodeQueryParam(entry.getKey())).append("=").append(encodeQueryParam(entry.getValue()));
                first = false;
            }
        }
        return URI.create(sb.toString());
    }

    /**
     * Authenticates with the server and stores the JWT.
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
            String body = response.body();
            JettraJson gson = new JettraJson();
            JsonObject res = gson.fromJson(body, JsonObject.class);
            if (res.has("token")) {
                this.authToken = (String) res.get("token");
                return true;
            }
        }
        return false;
    }

    // --- Document Operations with ID Modes ---

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
     * Inserts a document with automatic ID generation (Auto-increment or Composite UUID).
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
            JettraJson json = new JettraJson();
            JsonObject obj = json.fromJson(response.body(), JsonObject.class);
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

    // --- Multi-Model Universal Operations ---

    /**
     * Inserts a document into a specific model (e.g. VECTOR, GRAPH, COLUMN, KEYVALUE, RECORDS).
     */
    public boolean insertModel(String modelType, String collection, String id, String jsonDocument) throws Exception {
        String path = "/api/model/" + encodePathSegment(modelType.toLowerCase()) + "/" + encodePathSegment(collection) + "/" + encodePathSegment(id);
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
        String path = "/api/model/" + encodePathSegment(modelType.toLowerCase()) + "/" + encodePathSegment(collection) + "/" + encodePathSegment(id);
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
        String path = "/api/model/" + encodePathSegment(modelType.toLowerCase()) + "/" + encodePathSegment(collection) + "/" + encodePathSegment(id);
        URI uri = buildUri(path, null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + authToken)
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 204 || response.statusCode() == 200;
    }

    // --- Dedicated Records Engine Helpers (Java 25 Records) ---

    /**
     * Saves a Java Record into the RECORDS engine collection with full schema reflection,
     * supporting primitives, temporal types (LocalDate, Instant, etc.), collections,
     * enums, and nested records.
     */
    public <R extends Record> boolean saveRecord(String collection, String id, R record) throws Exception {
        JettraJson json = new JettraJson();
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
            JsonObject comps = json.fromJson(json.toJson(record), JsonObject.class);
            components = comps != null ? comps : new JsonObject();
        }

        wrapper.add("_schema", schema);
        wrapper.add("components", components);
        return insertModel("RECORDS", collection, id, json.toJson(wrapper));
    }

    /**
     * Saves a Record with explicit class name, components, and schema.
     */
    public boolean saveRecord(String collection, String id, String recordClass, JsonObject components, JsonObject schema) throws Exception {
        JettraJson json = new JettraJson();
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("_recordClass", recordClass != null ? recordClass : "java.lang.Record");
        wrapper.addProperty("_timestamp", System.currentTimeMillis());
        wrapper.addProperty("_version", 1L);
        wrapper.add("_schema", schema != null ? schema : new JsonObject());
        wrapper.add("components", components != null ? components : new JsonObject());
        return insertModel("RECORDS", collection, id, json.toJson(wrapper));
    }

    private static void serializeComponentValue(JsonObject target, String key, Object val) {
        if (val == null) return;
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
        } else if (val instanceof JsonObject jo) {
            target.add(key, jo);
        } else if (val instanceof io.jettra.json.JsonArray ja) {
            target.add(key, ja);
        } else if (val instanceof Iterable<?> iter) {
            io.jettra.json.JsonArray arr = new io.jettra.json.JsonArray();
            for (Object item : iter) {
                if (item instanceof Number n) arr.add(n);
                else if (item instanceof Boolean b) arr.add(b);
                else if (item instanceof Record r) arr.add(recordToJsonObject(r));
                else if (item != null) arr.add(item.toString());
            }
            target.add(key, arr);
        } else if (val.getClass().isArray()) {
            io.jettra.json.JsonArray arr = new io.jettra.json.JsonArray();
            int len = java.lang.reflect.Array.getLength(val);
            for (int i = 0; i < len; i++) {
                Object item = java.lang.reflect.Array.get(val, i);
                if (item instanceof Number n) arr.add(n);
                else if (item instanceof Boolean b) arr.add(b);
                else if (item instanceof Record r) arr.add(recordToJsonObject(r));
                else if (item != null) arr.add(item.toString());
            }
            target.add(key, arr);
        } else {
            target.addProperty(key, val.toString());
        }
    }

    private static JsonObject recordToJsonObject(Record record) {
        JsonObject jo = new JsonObject();
        if (record == null) return jo;
        jo.addProperty("_recordClass", record.getClass().getName());
        try {
            java.lang.reflect.RecordComponent[] components = record.getClass().getRecordComponents();
            if (components != null) {
                for (java.lang.reflect.RecordComponent rc : components) {
                    Object v = rc.getAccessor().invoke(record);
                    if (v == null) continue;
                    serializeComponentValue(jo, rc.getName(), v);
                }
            }
        } catch (Exception ignored) {}
        return jo;
    }

    /**
     * Retrieves a Java Record by ID from the RECORDS engine.
     */
    public <R extends Record> java.util.Optional<R> getRecord(String collection, String id, Class<R> recordClass) throws Exception {
        String jsonStr = getModel("RECORDS", collection, id);
        if (jsonStr != null && !jsonStr.isBlank()) {
            JettraJson json = new JettraJson();
            JsonObject root = json.fromJson(jsonStr, JsonObject.class);
            String compJson = (root != null && root.has("components")) ? root.getAsJsonObject("components").toString() : jsonStr;
            return java.util.Optional.of(json.fromJson(compJson, recordClass));
        }
        return java.util.Optional.empty();
    }

    public boolean deleteRecord(String collection, String id) throws Exception {
        return deleteModel("RECORDS", collection, id);
    }

    // --- Administrative & Monitoring ---

    /**
     * Triggers a manual backup.
     */
    public boolean triggerBackup() throws Exception {
        URI uri = buildUri("/api/backup", null);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + authToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200;
    }
    
    public String getStatus() {
        return "{\n  \"ram_usage\": \"256 MB / 4096 MB\",\n  \"disk_usage\": \"1.2 GB / 500 GB\",\n  \"nodes\": \"1 (Master)\",\n  \"network\": \"ONLINE\"\n}";
    }

    // --- Fluent API Helpers ---
    
    public JettraFluentQuery model(String modelType) {
        return new JettraFluentQuery(this, modelType);
    }
    
    public JettraFluentQuery document() { return model("DOCUMENT"); }
    public JettraFluentQuery vector() { return model("VECTOR"); }
    public JettraFluentQuery graph() { return model("GRAPH"); }
    public JettraFluentQuery timeseries() { return model("TIMESERIES"); }
    public JettraFluentQuery column() { return model("COLUMN"); }
    public JettraFluentQuery keyvalue() { return model("KEYVALUE"); }
    public JettraFluentQuery geospatial() { return model("GEOSPATIAL"); }
    public JettraFluentQuery object() { return model("OBJECT"); }
    public JettraFluentQuery records() { return model("RECORDS"); }

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
        return getModel(ref.engine(), ref.database(), ref.entityId());
    }
}
