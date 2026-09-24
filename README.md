# JettraStoreDriverJava

Official Java Driver for **JettraStoreEngine** with native support for **Java 25 Records**, Typed Repositories, Fluent Queries, and all 9 Multi-Model Database Engines.

## Installation (Maven)

```xml
<dependency>
    <groupId>com.jettra</groupId>
    <artifactId>JettraStoreDriverJava</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Quickstart with Java 25 Records & Rich Data Types

JettraStoreDriverJava natively supports:
- **Temporal types**: `Date`, `LocalDate`, `LocalTime`, `LocalDateTime`, `Instant`, `ZonedDateTime`, `OffsetDateTime`
- **Primitives**: `byte`, `short`, `int`, `long`, `float`, `double`, `boolean`, `char` (and wrappers)
- **Collections**: `List<>`, `Array<>`, `Set<>`, `Collection<>`
- **Enumerations**: `Enum<>`
- **Nested Objects & Records**: Deep record graphs (e.g. `Persona` -> `Pais`)

```java
import com.jettra.driver.java.JettraClient;
import com.jettra.driver.java.JettraRepository;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

// 1. Define Enums and Nested Java 25 Records
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

public class App {
    public static void main(String[] args) throws Exception {
        // 2. Connect and authenticate
        JettraClient client = new JettraClient("localhost", 8086);
        client.connect();
        client.login("admin", "admin");

        // 3. Direct Reflection saveRecord (automatically extracts schema & nested components)
        Persona p = new Persona(
            "PER-001",
            "Aristides",
            new Pais("PA", "Panamá"),
            LocalDate.of(1980, 5, 20),
            Instant.now(),
            List.of("java", "databases", "cloud"),
            EstadoCivil.CASADO
        );
        client.saveRecord("personas", p.id(), p);

        // 4. Retrieve typed Record
        Optional<Persona> loaded = client.getRecord("personas", "PER-001", Persona.class);
        loaded.ifPresent(pers -> System.out.println("Loaded: " + pers.nombre() + " from " + pers.pais().nombre()));

        // 5. Typed Record Repository Pattern
        JettraRepository<Persona> repo = client.recordRepository(Persona.class, "personas");
        Optional<Persona> found = repo.findById("PER-001");

        // 6. Fluent Records Query API
        client.records().collection("personas").insert("PER-002", 
            "{\"_recordClass\":\"Persona\",\"components\":{\"nombre\":\"Bob\",\"fechaNacimiento\":\"1992-08-14\"}}");

        client.close();
    }
}
```

## Multi-Model Engines Overview

| Engine | Fluent Method | Example |
| :--- | :--- | :--- |
| **`RECORDS`** | `client.records()` / `client.recordRepository(...)` | `repo.save("id", new MyRecord(...))` |
| **`DOCUMENT`** | `client.document()` | `client.document().collection("c").insert("id", json)` |
| **`VECTOR`** | `client.vector()` | `client.vector().collection("c").insert("id", json)` |
| **`GRAPH`** | `client.graph()` | `client.graph().collection("c").insert("id", json)` |
| **`TIMESERIES`**| `client.timeseries()` | `client.timeseries().collection("c").insert("ts", json)` |
| **`COLUMN`** | `client.column()` | `client.column().collection("c").insert("id", json)` |
| **`KEYVALUE`** | `client.keyvalue()` | `client.keyvalue().collection("c").insert("k", val)` |
| **`GEOSPATIAL`**| `client.geospatial()` | `client.geospatial().collection("c").insert("id", json)` |
| **`OBJECT`** | `client.object()` | `client.object().collection("c").insert("id", json)` |
