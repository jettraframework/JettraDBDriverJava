# JettraDBDriverJava

Driver oficial para **JettraDB** (y JettraStoreEngine) desarrollado para **Java 25** con soporte para Virtual Threads, Compact Object Headers, Generational ZGC, **Java 25 Records**, Repositorios Tipados, Consultas Fluent, Referencias Cruzadas `jref://` y los **9 Motores Multi-Modelo**.

## Instalación (Maven)

```xml
<dependencies>
    <dependency>
        <groupId>com.jettra</groupId>
        <artifactId>JettraDBDriverJava</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
</dependencies>

<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

## Características Principales

- **Gestión de Bases de Datos**: `listDatabases()`, `databaseExists()`, `createDatabase()`, `dropDatabase()`.
- **9 Motores Multi-Modelo Nativos**:
  - `RECORDS`: Serialización automática de Records Java 25 y proyección selectiva de campos (`?fields=...`).
  - `DOCUMENT`: CRUD NoSQL, secuencias e ID UUID, historial MVCC y restauración PITR.
  - `KEYVALUE`: Almacén atómico clave-valor en MemTable.
  - `VECTOR`: Embeddings IA y similitud coseno.
  - `GRAPH`: Grafo de propiedades con nodos y aristas.
  - `TIMESERIES`: Métricas y telemetría temporal.
  - `COLUMN`: Almacén columnar OLAP.
  - `GEOSPATIAL`: Puntos GPS y distancias Haversine.
  - `OBJECT`: BLOBs binarios y flujos serializados.
- **Referencias Cruzadas O(1) (`JettraReference`)**: Conexión directa entre motores mediante punteros `jref://[ENGINE:]db/id`.
- **Conversión Directa a Java 25 Records (`JettraRecordMapper`)**: Soporte directo en consultas (`client.query()`, `client.listRecords()`, `fluent.filter()`, `repo.find()`, `agg.toRecordList()`) con coerción automática de tipos, tolerancia a `camelCase`/`snake_case` y desempaquetado de bloques `components`.
- **Motores de Consultas Integrados**: Soporte y documentación para consultas declarativas **JQL (SQL)**, canalizaciones funcionales **Stream Pipeline**, analítica **JettraAggregation** y motores especializados (`VECTOR`, `GEOSPATIAL`, `TIMESERIES`, `GRAPH`, `COLUMN`, `DOCUMENT`, `RECORDS`).
- **Motor de Agregaciones y Agrupaciones (`JettraAggregation`)**: Pipeline analítico en memoria con `groupBy` (admite dot-notation), `count`, `sum`, `avg`, `min`, `max`, filtros `filter` (WHERE) y `having`, ordenamiento, paginación y resolución automática de referencias (`lookup`).
- **API Fluent (`JettraFluentQuery`)** y **Patrón Repositorio Tipado (`JettraRepository<T>`)**.
- **Autenticación y Sesiones**: Login JWT Bearer, cambio de clave y logout.
- **Caso de Estudio Completo**: Demostración con la base de datos empresarial **`ExampleFactura`**.

## Documentación Completa y Ejemplos

Consulte el manual completo con ejemplos de código para todas las operaciones y el caso de estudio de `ExampleFactura` en:
👉 **[Guía Completa y Manual de Arquitectura (guide/book.md)](guide/book.md)**
