# JettraStoreDriverJava - Comprehensive Guide & Architecture Manual

## 1. Overview & Architecture
`JettraStoreDriverJava` is the official, high-performance Java 25 client driver for **JettraStoreEngine**. It communicates via HTTP REST and Raft consensus protocols, providing seamless access to all 9 multi-model database engines:
1. **RECORDS**: Native storage for Java 25 `Record` entities with schema reflection and component projections.
2. **DOCUMENT**: Hierarchical NoSQL JSON documents with full CRUD and query support.
3. **VECTOR**: AI vector embeddings with cosine similarity and Top-K nearest neighbor search.
4. **GRAPH**: Labeled Property Graph (LPG) with nodes, edges, weights, and graph traversals.
5. **TIMESERIES**: High-frequency metric telemetry and IoT sensor data.
6. **COLUMN**: Wide-column OLAP tables and columnar projections.
7. **KEYVALUE**: High-speed in-memory cache and atomic string operations.
8. **GEOSPATIAL**: 2D GIS points with Haversine spherical distance calculation.
9. **OBJECT**: Binary BLOBs, chunked blocks, and Base64 media streams.

## 1.1 Unified Hierarchical Data Model
Every storage engine in JettraStoreEngine follows the canonical 3-level abstraction hierarchy:
```text
JettraStoreEngine
 └── EngineContext (e.g., DocumentEngine, VectorEngine, TimeSeriesEngine)
      └── StorageContainer (Database / Keyspace / Bucket / Graph)
           └── StorageUnit (Collection / Metric / Layer / Index / ColumnFamily)
                └── StorageItem (Document / Point / Feature / Vector / KeyValue / Node)
```

| Engine Model | Level 1: StorageContainer | Level 2: StorageUnit | Level 3: StorageItem |
| --- | --- | --- | --- |
| **DOCUMENT** | Database | Collection | Document (JSON / BSON) |
| **KEYVALUE** | Database / Keyspace | Bucket / Namespace | Key-Value Pair |
| **COLUMN** | Keyspace | Column Family / Table | Row Key + Dynamic Columns |
| **GRAPH** | Graph Database | Node/Edge Label | Vertex (Node) & Edge |
| **VECTOR** | Vector DB / Catalog | Vector Index / Collection | Vector (float[]) + Payload |
| **TIMESERIES** | Database / Bucket | Metric / Measurement | Data Point (Timestamp + Values) |
| **GEOSPATIAL** | Spatial DB / Catalog | Spatial Layer / Feature Set | Feature (Geometry + Attrs) |
| **OBJECT** | Storage Account / Tenant | Bucket / Container | Blob Object + Metadata |
| **RECORDS** | Database / Schema | Record Table / Entity Set | Immutable Java 25 Record |

---

## 2. Key Features
- **Java 25 Records First-Class Support**: Serialization, schema extraction, and automatic deserialization of immutable records.
- **Typed Repository Pattern**: `JettraRepository<T>` for domain-driven persistence without boilerplate.
- **Fluent Query API**: Method chaining for multi-model database operations.
- **JWT Authentication & Security**: Built-in token management with role-based access control.
- **Zero Heavy Dependencies**: Lightweight, fast startup, optimized for Virtual Threads (Project Loom).

---

## 3. Installation & Setup

### Maven Dependency
```xml
<dependency>
    <groupId>com.jettra</groupId>
    <artifactId>JettraStoreDriverJava</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## 4. Usage & Code Examples

### 4.1 Connecting & Authenticating
```java
import com.jettra.driver.java.JettraClient;

public class ClientDemo {
    public static void main(String[] args) throws Exception {
        JettraClient client = new JettraClient("localhost", 8086);
        client.connect();
        
        boolean auth = client.login("admin", "admin");
        if (auth) {
            System.out.println("Authenticated successfully.");
        }
    }
}
```

### 4.2 Records Engine: Typed Java 25 Records & Supported Types

`JettraStoreDriverJava` supports canonical reflection and serialization for:
- **Temporal types**: `Date`, `LocalDate`, `LocalTime`, `LocalDateTime`, `Instant`, `ZonedDateTime`, `OffsetDateTime`.
- **Primitives**: `byte`, `short`, `int`, `long`, `float`, `double`, `boolean`, `char`.
- **Collections & Lists**: `List<>`, `Array<>`, `Set<>`, `Collection<>`.
- **Enumerations**: `Enum<>`.
- **Nested Objects & Records**: Deep domain record trees (e.g. `Persona` containing `Pais`).

```java
import com.jettra.driver.java.JettraClient;
import com.jettra.driver.java.JettraRepository;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public enum EstadoCivil { SOLTERO, CASADO, DIVORCIADO }
public record Pais(String codigo, String nombre) {}
public record Persona(
    String id,
    String nombre,
    Pais pais,
    LocalDate fechaNacimiento,
    Instant creadoEn,
    List<String> tags,
    EstadoCivil estadoCivil
) {}

public class RecordsDemo {
    public static void main(String[] args) throws Exception {
        JettraClient client = new JettraClient("localhost", 8086);
        client.connect();
        client.login("admin", "admin");

        // 1. Direct Reflection saveRecord (schema automatically extracted)
        Persona p = new Persona(
            "PER-001",
            "Aristides",
            new Pais("PA", "Panamá"),
            LocalDate.of(1980, 5, 20),
            Instant.now(),
            List.of("java", "databases", "microservices"),
            EstadoCivil.CASADO
        );
        client.saveRecord("personas", p.id(), p);

        // 2. Using Typed Record Repository
        JettraRepository<Persona> repo = client.recordRepository(Persona.class, "personas");
        Optional<Persona> found = repo.findById("PER-001");
        found.ifPresent(pers -> System.out.println("Found: " + pers.nombre() + " from " + pers.pais().nombre()));

        // 3. Direct Record retrieval
        Optional<Persona> loaded = client.getRecord("personas", "PER-001", Persona.class);

        // 4. Fluent Records API
        client.records().collection("personas").insert("PER-002", 
            "{\"_recordClass\":\"Persona\",\"components\":{\"nombre\":\"Bob\",\"fechaNacimiento\":\"1992-08-14\"}}");
        String rawJson = client.records().collection("personas").get("PER-002");

        // Delete
        repo.delete("PER-001");
        client.deleteRecord("personas", "PER-002");
    }
}
```

### 4.3 Multi-Model Fluent Query API
```java
// Document Engine
client.document().collection("users").insert("U1", "{\"name\":\"John\",\"status\":\"ACTIVE\"}");
String user = client.document().collection("users").get("U1");

// Vector Engine
client.vector().collection("embeddings").insert("V1", "{\"vector\":[0.1, 0.9, 0.4],\"label\":\"product_a\"}");

// KeyValue Engine
client.keyvalue().collection("cache").insert("session_token", "abc-123");

// TimeSeries Engine
client.timeseries().collection("metrics").insert("1755735000000", "{\"cpu\": 42.5, \"memory\": 78.0}");
```

### 4.4 ObjectId / DocumentId Generation Modes
`JettraStoreDriverJava` supports 3 flexible ID generation strategies:
1. **Manual (`IdMode.MANUAL`)**: Custom ID specified by the caller.
2. **Auto-increment (`IdMode.AUTOINCREMENT`)**: Sequential numeric ID managed internally by the database (`1, 2, 3...`).
3. **Composite UUID (`IdMode.UUID`)**: Globally unique identifier combining CPU/Host signature, millisecond timestamp, database/collection digest, and random UUID entropy.

```java
import com.jettra.driver.java.JettraClient;
import com.jettra.driver.java.JettraClient.IdMode;

// 1. Manual ID
client.insertDocument("orders", "ORD-1001", "{\"item\":\"Server Rack\",\"qty\":2}", IdMode.MANUAL);

// 2. Auto-increment Sequence
String autoId1 = client.insertDocumentAuto("invoices", "{\"total\": 450.00}", IdMode.AUTOINCREMENT);
String autoId2 = client.insertDocumentAuto("invoices", "{\"total\": 890.50}", IdMode.AUTOINCREMENT);
System.out.println("Generated Sequences: " + autoId1 + ", " + autoId2); // e.g. 1, 2

// 3. Composite UUID
String uuidId = client.insertDocumentAuto("events", "{\"event\":\"login\",\"user\":\"admin\"}", IdMode.UUID);
System.out.println("Generated Composite UUID: " + uuidId); // e.g. 8a7f1c2d-18dc93a4-a1b2-9f82ab34
```

### 4.5 Version History & Point-in-Time Restoration
```java
// Inspect all historical versions of a document
String historyJson = client.getDocumentHistory("orders", "ORD-1001");
System.out.println("Version History: " + historyJson);

// Restore to a specific version timestamp
long targetTimestamp = 1755735000000L;
boolean restored = client.restoreDocumentVersion("orders", "ORD-1001", targetTimestamp);
if (restored) {
    System.out.println("Document successfully rolled back to timestamp " + targetTimestamp);
}
```

