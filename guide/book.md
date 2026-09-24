# JettraDBDriverJava - Guía Completa de Uso y Manual de Arquitectura

`JettraDBDriverJava` es el driver oficial de ultra-alto rendimiento para **JettraDB** (y JettraStoreEngine), desarrollado nativamente para **Java 25** aprovechando Virtual Threads (Project Loom), Compact Object Headers y Generational ZGC.

Proporciona acceso transparente, concurrente y tipado a los **9 motores multi-modelo de base de datos**:
1. **`RECORDS`**: Entidades Java 25 `Record` inmutables con introspección de esquema y proyecciones (`?fields=...`).
2. **`DOCUMENT`**: Documentos jerárquicos NoSQL JSON/BSON con autoincremento, UUIDs, historial MVCC y restauración PITR.
3. **`VECTOR`**: Embeddings para Inteligencia Artificial con similitud coseno y búsqueda Top-K.
4. **`GRAPH`**: Grafo de propiedades (LPG) con nodos, aristas dirigidas, etiquetas y relaciones.
5. **`TIMESERIES`**: Telemetría temporal de alta frecuencia, métricas IoT y timestamps en microsegundos.
6. **`COLUMN`**: Familias de columnas anchas (Wide-Column) para análisis OLAP y proyecciones tabulares.
7. **`KEYVALUE`**: Almacén clave-valor atómico ultrarrápido directamente en la MemTable.
8. **`GEOSPATIAL`**: Coordenadas 2D GPS y cálculos de proximidad esférica mediante la fórmula de Haversine.
9. **`OBJECT`**: BLOBs binarios, metadatos de clases y flujos de objetos serializados.

---

## 1. Instalación y Configuración (Maven)

Agregue la dependencia y el repositorio a su archivo `pom.xml`:

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

---

## 2. Conexión y Gestión de Seguridad

### 2.1 Conexión y Autenticación con Token Bearer

```java
import com.jettra.driver.java.JettraClient;

public class App {
    public static void main(String[] args) throws Exception {
        // Conexión al host y puerto REST de JettraDB (por defecto 8086)
        JettraClient client = new JettraClient("localhost", 8086);
        client.connect();

        // Autenticación con credenciales del sistema
        boolean ok = client.login("admin", "admin");
        if (ok) {
            System.out.println("✓ Autenticado con éxito. Token: " + client.getAuthToken());
        }

        // Comprobación de estado
        if (client.isAuthenticated()) {
            System.out.println("Sesión activa lista para operaciones.");
        }

        // Cierre de sesión y desconexión
        client.logout();
        client.close();
    }
}
```

### 2.2 Cambio de Contraseña de Usuario

```java
boolean changed = client.changePassword("super-user", "superUserZ", "NuevaClaveSegura2026!");
if (changed) {
    System.out.println("Contraseña actualizada correctamente.");
}
```

---

## 3. Gestión del Ciclo de Vida de Bases de Datos (`/api/databases`)

JettraDB organiza los datos en particiones aisladas e independientes. El driver permite administrar el ciclo de vida completo de cada base de datos:

```java
import java.util.List;

// 1. Listar todas las bases de datos activas en el nodo
List<String> databases = client.listDatabases();
System.out.println("Bases de datos disponibles: " + databases);

// 2. Comprobar si una base de datos existe
boolean existe = client.databaseExists("FacturacionEmpresa");
System.out.println("¿Existe FacturacionEmpresa? " + existe);

// 3. Crear una nueva partición de base de datos
if (!existe) {
    boolean creada = client.createDatabase("FacturacionEmpresa");
    System.out.println("Base de datos creada: " + creada);
}

// 4. Eliminar una base de datos y purgar todo su almacenamiento
boolean eliminada = client.dropDatabase("BaseDeDatosTemporal");
System.out.println("Base de datos eliminada: " + eliminada);
```

---

## 4. Operaciones CRUD en los 9 Motores Multi-Modelo

### 4.1 Motor `RECORDS` (Java 25 Records y Proyección de Campos)

El motor `RECORDS` serializa automáticamente estructuras `java.lang.Record` extrayendo el esquema, tipos temporales (`LocalDate`, `Instant`), enumeraciones, listas y registros anidados.

```java
import com.jettra.driver.java.JettraClient;
import io.jettra.json.JsonObject;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Definición de Registros Java 25
public enum CategoriaEmpleado { PRINCIPAL, SENIOR, JUNIOR }
public record Contacto(String email, String telefono) {}
public record Empleado(
    String id,
    String nombre,
    String departamento,
    double salario,
    CategoriaEmpleado categoria,
    Contacto contacto,
    LocalDate fechaIngreso
) {}

public class RecordsExample {
    public static void main(String[] args) throws Exception {
        JettraClient client = new JettraClient("localhost", 8086);
        client.connect();
        client.login("admin", "admin");

        Empleado emp = new Empleado(
            "EMP-042",
            "Diana Prince",
            "Ingeniería",
            4850.00,
            CategoriaEmpleado.PRINCIPAL,
            new Contacto("diana@empresa.com", "+507 6000-1234"),
            LocalDate.of(2022, 3, 15)
        );

        // CREATE / UPDATE: Guarda el registro con extracción automática de esquema
        client.saveRecord("rrhh", emp.id(), emp);

        // READ: Recupera el registro fuertemente tipado
        Optional<Empleado> recuperado = client.getRecord("rrhh", "EMP-042", Empleado.class);
        recuperado.ifPresent(e -> System.out.println("Empleado: " + e.nombre() + " | Salario: $" + e.salario()));

        // READ CON PROYECCIÓN (?fields=nombre,salario): Optimización de red
        Optional<JsonObject> campos = client.getRecordFields("rrhh", "EMP-042", List.of("nombre", "salario"));
        campos.ifPresent(c -> System.out.println("Proyección: " + c));

        // DELETE
        client.deleteRecord("rrhh", "EMP-042");
    }
}
```

---

### 4.2 Motor `DOCUMENT` (NoSQL JSON, Generación de IDs y Versionado MVCC)

Permite almacenar árboles JSON libres de esquema con múltiples estrategias de generación de identificadores (`MANUAL`, `AUTOINCREMENT`, `UUID`), historial de versiones y restauración a punto en el tiempo (PITR).

```java
import com.jettra.driver.java.JettraClient;
import com.jettra.driver.java.JettraClient.IdMode;
import com.jettra.driver.java.DocumentVersion;
import java.util.List;

// 1. Inserción con ID Manual
client.insertDocument("clientes", "CLI-100", "{\"nombre\":\"Acme Corp\",\"saldo\":1200.0}", IdMode.MANUAL);

// 2. Inserción con Auto-Incremento (1, 2, 3...)
String idAuto = client.insertDocumentAuto("tickets", "{\"asunto\":\"Fallo de red\",\"prioridad\":\"ALTA\"}", IdMode.AUTOINCREMENT);
System.out.println("ID Secuencial generado: " + idAuto);

// 3. Inserción con UUID Compuesto Criptográfico
String idUuid = client.insertDocumentAuto("auditoria", "{\"evento\":\"LOGIN_SUCCESS\",\"usuario\":\"admin\"}", IdMode.UUID);
System.out.println("ID UUID generado: " + idUuid);

// 4. Lectura de documento individual
String doc = client.getDocument("clientes", "CLI-100");
System.out.println("Documento: " + doc);

// 5. Listado de todos los documentos en una colección
List<String> todosLosClientes = client.listDocuments("clientes");
System.out.println("Total clientes: " + todosLosClientes.size());

// 6. Historial de Versiones MVCC
List<DocumentVersion> versiones = client.getDocumentHistoryVersions("clientes", "CLI-100");
for (DocumentVersion v : versiones) {
    System.out.printf("Rev #%d | Fecha: %s | Actual: %b%n", v.versionNumber(), v.formattedDate(), v.isCurrent());
}

// 7. Restauración a Punto en el Tiempo (PITR)
if (!versiones.isEmpty()) {
    long timestampAnterior = versiones.get(0).timestamp();
    client.restoreDocumentVersion("clientes", "CLI-100", timestampAnterior);
}

// 8. Eliminación
client.deleteDocument("clientes", "CLI-100");
```

---

### 4.3 Motor `KEYVALUE` (Caché Atómica en Memoria)

```java
// Almacena un par clave-valor directamente en la MemTable
client.putKeyValue("sesiones", "token_usr_123", "{\"userId\":123,\"rol\":\"ADMIN\"}");

// Consulta directa O(1)
String sesion = client.getKeyValue("sesiones", "token_usr_123");
System.out.println("Sesión activa: " + sesion);

// Eliminación atómica
client.deleteKeyValue("sesiones", "token_usr_123");
```

---

### 4.4 Motor `VECTOR` (Embeddings IA y Similitud Coseno)

```java
import io.jettra.json.JsonObject;

JsonObject meta = new JsonObject();
meta.addProperty("titulo", "Manual de Arquitectura Rust y Java");
meta.addProperty("categoria", "DATABASE_SYSTEMS");

float[] embedding = new float[]{0.15f, 0.88f, 0.42f, 0.05f};
client.insertVector("libros_ia", "vec_doc_01", embedding, meta);

String vectorDoc = client.getVector("libros_ia", "vec_doc_01");
System.out.println("Vector registrado: " + vectorDoc);
```

---

### 4.5 Motor `GRAPH` (Grafos de Propiedades, Nodos y Relaciones)

```java
import io.jettra.json.JsonObject;
import io.jettra.json.JsonArray;

JsonObject nodo = new JsonObject();
nodo.addProperty("nombre", "Servidor Web Nginx");
nodo.addProperty("ip", "192.168.1.10");

JsonArray aristas = new JsonArray();
JsonObject arista = new JsonObject();
arista.addProperty("target", "db_primary_cluster");
arista.addProperty("relationship", "CONNECTS_TO");
aristas.add(arista);
nodo.add("edges", aristas);

client.addGraphNode("infraestructura", "node_nginx_01", nodo);
String grafo = client.getGraphNode("infraestructura", "node_nginx_01");
System.out.println("Nodo de grafo: " + grafo);
```

---

### 4.6 Motor `TIMESERIES` (Métricas IoT y Telemetría Temporal)

```java
import io.jettra.json.JsonObject;

long now = System.currentTimeMillis();
JsonObject metrica = new JsonObject();
metrica.addProperty("sensor", "temperatura_cpu_core_0");
metrica.addProperty("celsius", 48.7);
metrica.addProperty("ventilador_rpm", 2200);

client.insertTimeSeries("telemetria_servidores", now, metrica);
String tsData = client.getTimeSeries("telemetria_servidores", now);
System.out.println("Métrica registrada: " + tsData);
```

---

### 4.7 Motor `COLUMN` (Almacén OLAP de Familias de Columnas)

```java
import io.jettra.json.JsonObject;

JsonObject columnas = new JsonObject();
columnas.addProperty("cf:cliente", "Empresa Panameña S.A.");
columnas.addProperty("cf:total", 1450.75);
columnas.addProperty("cf:estado", "PAGADA");

client.insertColumnRow("ventas_2026", "factura_9981", columnas);
String fila = client.getColumnRow("ventas_2026", "factura_9981");
System.out.println("Fila columnar: " + fila);
```

---

### 4.8 Motor `GEOSPATIAL` (Coordenadas GPS y Mapas GIS)

```java
import io.jettra.json.JsonObject;

JsonObject sucursal = new JsonObject();
sucursal.addProperty("nombre", "Sucursal Costa del Este");
sucursal.addProperty("ciudad", "Ciudad de Panamá");

// Coordenadas: latitud 8.9824, longitud -79.5199
client.insertLocation("sucursales", "suc_01", 8.9824, -79.5199, sucursal);

String geo = client.getLocation("sucursales", "suc_01");
System.out.println("Punto geográfico: " + geo);
```

---

### 4.9 Motor `OBJECT` (BLOBs, Archivos y Estado Serializado)

```java
import io.jettra.json.JsonObject;

JsonObject estado = new JsonObject();
estado.addProperty("archivo", "contrato_firmado.pdf");
estado.addProperty("tamano_bytes", 248900);
estado.addProperty("sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

client.saveObject("documentos_legales", "doc_pdf_01", "ContratoDigital", estado);
String obj = client.getObject("documentos_legales", "doc_pdf_01");
System.out.println("Objeto binario registrado: " + obj);
```

---

## 5. API Fluent (`JettraFluentQuery`) y Repositorios Tipados

### 5.1 Consultas Fluidas con Method Chaining

```java
// Inserción en DOCUMENT
client.document().collection("usuarios").insert("U10", "{\"nombre\":\"Carlos\",\"rol\":\"MANAGER\"}");

// Verificación de existencia
boolean existe = client.document().collection("usuarios").exists("U10");

// Lectura de documento
String usuario = client.document().collection("usuarios").get("U10");

// Proyección fluida en RECORDS
Optional<Empleado> emp = client.records()
    .collection("rrhh")
    .fields("nombre", "salario")
    .get("EMP-042", Empleado.class);

// Eliminación fluida
client.document().collection("usuarios").delete("U10");
```

### 5.2 Patrón Repositorio Tipado (`JettraRepository<T>`)

```java
import com.jettra.driver.java.JettraRepository;
import java.util.List;
import java.util.Optional;

// Repositorio tipado para el registro Empleado
JettraRepository<Empleado> repo = client.recordRepository(Empleado.class, "rrhh");

// 1. Guardar entidad (deduce el ID automáticamente desde el componente id())
repo.save(emp);

// 2. Comprobar existencia por ID
if (repo.existsById("EMP-042")) {
    System.out.println("El empleado existe en la base de datos.");
}

// 3. Buscar por ID con proyección selectiva de campos
Optional<Empleado> proyectado = repo.findById("EMP-042", List.of("nombre", "salario"));

// 4. Eliminar por ID
repo.delete("EMP-042");
```

---

---

## 6. Motores de Consultas Integrados en JettraDB y Conversión Directa a Java Records

JettraDB integra múltiples motores de consulta especializados para adaptarse a la naturaleza políglota de los datos almacenados. El driver `JettraDBDriverJava` ofrece soporte directo de primera clase para ejecutar consultas sobre estos motores y convertir de forma automática y transparente los resultados en **Java 25 Records**.

```text
                                       Motores de Consulta en JettraDB
 ┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐
 │ 1. JQL Engine       : Consultas declarativas SQL (SELECT, FROM, WHERE, ORDER BY, LIMIT, SKIP)           │
 │ 2. Fluent Stream    : Canalizaciones funcionales con expresiones lambda (.filter, .map, .sorted, .limit)│
 │ 3. Aggregation      : Motor analítico en memoria (groupBy, count, sum, avg, min, max, having, lookup)   │
 │ 4. Vector Search    : Similitud semántica coseno y k-NN Top-K para embeddings de Inteligencia Artificial│
 │ 5. Geospatial Query : Búsqueda esférica por coordenadas GPS (lat, lon) y cálculo de radio Haversine     │
 │ 6. TimeSeries Query : Agregación por ventanas temporales y timestamps con precisión de microsegundos     │
 │ 7. Graph Traversal  : Navegación de adyacencia de nodos, aristas dirigidas y relaciones taxonómicas     │
 │ 8. Columnar OLAP    : Fact-tables tabulares, familias de columnas y proyecciones multidimensionales     │
 │ 9. Records Engine   : Consultas fuertemente tipadas con introspección de esquemas y proyección ?fields │
 └─────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                                     │
                                                     ▼
                                            JettraRecordMapper
                             (Coerción de tipos, desempaquetado de componentes,
                              tolerancia camelCase/snake_case, Instanciación O(1))
                                                     │
                                                     ▼
                                        Java 25 Records Inmutables
                                     (Compact Object Headers, Zero-GC)
```

---

### 6.1 Visión General de los Motores de Consultas de JettraDB

#### 1. Motor Declarativo JQL (JettraQueryLanguage)
Proporciona una interfaz declarativa similar a SQL que escanea particiones multi-modelo:
- **Sintaxis**:
  ```sql
  SELECT id, name, creditLimit, city 
  FROM ExampleFactura 
  WHERE status = 'ACTIVE' AND creditLimit >= 10000.0 
  ORDER BY creditLimit DESC 
  LIMIT 50 SKIP 0
  ```
- **Operadores soportados**: `=`, `!=`, `<>`, `>=`, `<=`, `>`, `<`, `LIKE`, `ILIKE`, `CONTAINS` y combinación lógica con `AND`.
- **Ordenamiento y Paginación**: Ordenamiento alfanumérico o numérico (`ORDER BY campo ASC|DESC`), límites (`LIMIT n`) y desplazamiento (`SKIP m` u `OFFSET m`).

#### 2. Motor de Flujos Funcionales Java 25 (Stream Pipeline API)
Permite construir consultas analíticas mediante una canalización funcional encadenada en memoria:
```java
// Consulta funcional sobre colección de clientes
List<ClienteRecord> clientes = client.document()
    .collection("customers")
    .filter(c -> "ACTIVE".equals(c.status()) && c.creditLimit() >= 25000.0, ClienteRecord.class);
```

#### 3. Motor de Búsqueda Vectorial (`VECTOR`)
Especializado en Inteligencia Artificial y bases de conocimiento:
- Ejecuta cálculos de **Similitud Coseno** y distancia Euclidiana sobre embeddings vectoriales de punto flotante (`float[]`).
- Devuelve los vecinos más cercanos (**k-NN Top-K**) ordenados por puntuación de relevancia o afinidad semántica.

#### 4. Motor de Consultas Geoespaciales (`GEOSPATIAL`)
- Permite almacenar coordenadas GPS bidimensionales (`lat`, `lon`).
- Ejecuta consultas de **geocercas y proximidad por radio** calculando la distancia esférica sobre la superficie terrestre mediante la **Fórmula de Haversine**:
  $$d = 2r \arcsin\left(\sqrt{\sin^2\left(\frac{\Delta \text{lat}}{2}\right) + \cos(\text{lat}_1)\cos(\text{lat}_2)\sin^2\left(\frac{\Delta \text{lon}}{2}\right)}\right)$$

#### 5. Motor de Series Temporales (`TIMESERIES`)
- Optimizado para flujos continuos de métricas IoT, telemetría y sensores.
- Soporta consultas de **rango temporal (`range`) y agregaciones por ventanas** con marcas de tiempo en milisegundos o microsegundos (`timestamp`).

#### 6. Motor de Grafos (`GRAPH`)
- Almacena grafos de propiedades (LPG) con nodos y aristas dirigidas.
- Permite ejecutar consultas de **adyacencia, exploración de relaciones** (por ejemplo, `SUB_CATEGORY_OF`, `REPORTS_TO`) y recorridos en anchura o profundidad (BFS/DFS).

#### 7. Motor Columnar (`COLUMN`)
- Agrupa datos en familias de columnas anchas (Wide-Column).
- Ideal para facturación y análisis OLAP donde se realizan lecturas masivas sobre un subconjunto específico de columnas sin penalizar I/O.

#### 8. Motor de Documentos (`DOCUMENT`)
- Almacenamiento NoSQL jerárquico JSON/BSON.
- Soporta asignación de ID automática (secuencial o UUID compuesto de hardware), consultas por colecciones e inspección de auditoría MVCC con restauración en el tiempo (PITR).

#### 9. Motor de Records (`RECORDS`)
- Especializado en entidades Java `Record` inmutables.
- Proporciona validación estricta de componentes de registro y **proyección selectiva de campos (`?fields=campo1,campo2`)**, transfiriendo por red únicamente las propiedades solicitadas.

---

### 6.2 Conversión Directa y Automática a Java Records (`JettraRecordMapper`)

Los **Java Records** introducidos de forma canónica en la plataforma Java proporcionan inmutabilidad garantizada, igualdad por valor y representación de memoria ultra-compacta. En **Java 25**, los Records se benefician de **Compact Object Headers** (reduciendo el overhead por objeto de 16/12 bytes a 8 bytes) y de la recolección concurrente sin pausas de **Generational ZGC**.

El componente [`JettraRecordMapper`](file:///home/avbravo/NetBeansProjects/jettrastack_local/JettraWorkspace/FolderStoreDB/JettraDBDriverJava/src/main/java/com/jettra/driver/java/JettraRecordMapper.java) implementa un motor de introspección reflexiva capaz de mapear cualquier resultado de consulta a un Record Java de forma instantánea.

#### Estrategias Inteligentes de Mapeo:
1. **Desempaquetado Automático de `components`**: El motor RECORDS de JettraDB almacena payloads estructurados en `{"components": {...}}`. `JettraRecordMapper` detecta este contenedor y extrae los campos directamente.
2. **Tolerancia a Formatos de Nombres**: Coincidencia insensible a mayúsculas/minúsculas, así como traducción automática bidireccional entre `camelCase` (ej. `totalAmount`) y `snake_case` (ej. `total_amount`).
3. **Mapeo Inteligente de Alias de Agregación**: Si un Record contiene el campo `totalAmount` y la agregación generó el acumulador `sum_totalAmount`, el mapper lo asocia automáticamente.
4. **Coerción de Tipos Nativos**:
   - Primitivos y Wrappers (`int`, `long`, `double`, `float`, `boolean`).
   - Fechas y Tiempos (`java.time.LocalDate`, `java.time.Instant`).
   - Identificadores UUID (`java.util.UUID`).
   - Decimales de alta precisión (`java.math.BigDecimal`).
   - Enumeraciones tipadas (`Enum<?>`).
   - Objetos JSON embebidos (`JsonObject`) y **Records anidados dentro de Records**.

#### Ejemplos de Conversión en Consultas del Driver:

```java
import com.jettra.driver.java.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Definición de Java 25 Records
public record Contacto(String email, String telefono) {}

public record ClienteRecord(
    String id,
    String name,
    String city,
    String customerType,
    double creditLimit,
    String status,
    Contacto contact,
    LocalDate registrationDate
) {}

public record ResumenVentasSucursal(
    String branchRef,
    long cantidadFacturas,
    double ventasTotales,
    double ticketPromedio
) {}
```

#### 1. Lectura por ID con Conversión Directa a Record:
```java
// Obtiene el registro tipado directamente desde el motor RECORDS o DOCUMENT
Optional<ClienteRecord> clienteOpt = client.getRecord("customers", "cust_101", ClienteRecord.class);
clienteOpt.ifPresent(c -> 
    System.out.printf("Cliente: %s | Ciudad: %s | Límite: $%.2f%n", c.name(), c.city(), c.creditLimit())
);
```

#### 2. Listado Completo de una Colección Tipada:
```java
// Recupera y convierte en paralelo todos los documentos a ClienteRecord
List<ClienteRecord> listaClientes = client.listRecords("customers", ClienteRecord.class);
System.out.println("Total clientes cargados: " + listaClientes.size());
```

#### 3. Consultas con Filtro en Memoria Tipado:
```java
// Consulta tipada con predicado Java
List<ClienteRecord> corporativos = client.query("customers", 
    c -> "CORPORATE".equals(c.customerType()) && c.creditLimit() >= 50000.0,
    ClienteRecord.class
);
corporativos.forEach(c -> System.out.println("Corporativo premium: " + c.name()));
```

#### 4. Consultas Fluidas con Method Chaining:
```java
// Utilizando la Fluent API
List<ClienteRecord> activos = client.document()
    .collection("customers")
    .filter(c -> "ACTIVE".equals(c.status()), ClienteRecord.class);
```

#### 5. Resolución Tipada de Referencias Cruzadas `jref://`:
```java
// Resuelve el puntero cruzado y lo transforma directamente en el Record correspondiente
Optional<ClienteRecord> refCliente = client.resolveRef("jref://DOCUMENT:ExampleFactura/cust_1", ClienteRecord.class);
refCliente.ifPresent(c -> System.out.println("Referencia resuelta: " + c.name()));
```

#### 6. Transformación Directa de Agregaciones a Java Records:
```java
// Convierte todo el resultado del pipeline de agregación a una lista de Records
List<ResumenVentasSucursal> resumenes = client.aggregate("invoices")
    .groupBy("branchRef")
    .count("cantidadFacturas")
    .sum("totalAmount", "ventasTotales")
    .avg("totalAmount", "ticketPromedio")
    .execute()
    .toRecordList(ResumenVentasSucursal.class);

for (ResumenVentasSucursal r : resumenes) {
    System.out.printf("Sucursal: %s | Ventas: $%.2f | Tickets: %d | Promedio: $%.2f%n",
        r.branchRef(), r.ventasTotales(), r.cantidadFacturas(), r.ticketPromedio());
}
```

---

## 7. Referencias Cruzadas Directas (`JettraReference` - `jref://`)

Las referencias cruzadas permiten conectar registros entre motores distintos de manera directa, con resolución O(1) sin costosos JOINs relacionales:

### Formato URI Canónico:
```text
jref://[nodo@][MOTOR:]base_de_datos/id_entidad
```

Ejemplos:
- `jref://RECORDS:ExampleFactura/comp_1`
- `jref://DOCUMENT:ExampleFactura/cust_42`
- `jref://GEOSPATIAL:ExampleFactura/sucursal_3`
- `jref://COLUMN:ExampleFactura/fac_100`

### Uso en el Driver:

```java
import com.jettra.driver.java.JettraReference;

// 1. Construcción de referencias
JettraReference refEmpresa = client.createRef("RECORDS", "ExampleFactura", "comp_1");
JettraReference refSucursal = client.createRef("GEOSPATIAL", "ExampleFactura", "sucursal_3");

System.out.println("URI: " + refEmpresa.toUri()); // jref://RECORDS:ExampleFactura/comp_1
System.out.println("Storage Key: " + refEmpresa.directStorageKey()); // rec:ExampleFactura:comp_1

// 2. Conversión a objeto JSON embebible
JsonObject refJson = refEmpresa.toJsonObject(); // {"$jref": "jref://RECORDS:ExampleFactura/comp_1"}

// 3. Resolución directa del registro apuntado
String datosEmpresa = client.resolveRef(refEmpresa);
System.out.println("Datos resueltos de la empresa: " + datosEmpresa);

// 4. Resolución tipada directa a Java Record
Optional<EmpresaRecord> empresaRec = client.resolveRef(refEmpresa, EmpresaRecord.class);
empresaRec.ifPresent(e -> System.out.println("Empresa tipada: " + e.name()));
```

---

## 8. Motor de Agregaciones y Agrupaciones (Aggregation & Grouping API)

`JettraDBDriverJava` incorpora un potente motor nativo de agregación y agrupación fluida (`JettraAggregation`) diseñado para procesar consultas analíticas complejas directamente en memoria con latencia ultra-baja y paralelismo en Java 25.

### 8.1 Arquitectura del Pipeline y Componentes

El motor de agregación se compone de tres elementos principales:
1. **`JettraAggregation`**: Constructor fluido del pipeline de agregación donde se configuran grupos, acumuladores, filtros y ordenamiento.
2. **`AggregationResult`**: Conjunto de resultados iterable, indexable, transformable a Records (`toRecordList(...)`) y serializable a JSON (`toJson()`, `toJsonArray()`).
3. **`AggregationRow`**: Fila individual agregada que contiene las claves de agrupación (`_id`), los valores acumulados, los objetos enriquecidos vía referencias cruzadas y el método `toRecord(Record.class)`.

```text
  Documentos / Registros JSON / Entidades Java 25
                         │
                         ▼
             [ .filter(Predicate) ]        <-- Pre-filtro (WHERE / $match)
                         │
                         ▼
             [ .groupBy("campo", ...) ]    <-- Clave simple, compuesta o anidada
                         │
                         ▼
        [ .count(), .sum(), .avg(), ... ]   <-- Funciones acumuladoras
                         │
                         ▼
             [ .lookup("ref", alias) ]     <-- Enriquecimiento O(1) con JettraReference
                         │
                         ▼
             [ .having(Predicate) ]        <-- Post-filtro (HAVING)
                         │
                         ▼
         [ .sortBy(...), .skip(), .limit() ]<-- Orden y Paginación
                         │
                         ▼
                 AggregationResult
                         │
                         ├──▶ toRecordList(RecordClass.class)  (Java 25 Records)
                         └──▶ toJson() / toJsonArray()         (Payloads JSON)
```

### 8.2 Agrupaciones Simples, Compuestas y Campos Anidados

El método `groupBy(...)` admite una o múltiples claves, así como acceso a propiedades profundamente anidadas mediante **notación de punto (`dot.notation`)**:

```java
// 1. Agrupación simple por un campo
JettraAggregation agg1 = client.aggregate("clientes")
        .groupBy("city")
        .count("totalClientes");

// 2. Agrupación compuesta por múltiples campos
JettraAggregation agg2 = client.aggregate("clientes")
        .groupBy("city", "customerType")
        .count("total")
        .avg("creditLimit", "creditoPromedio");

// 3. Agrupación sobre campos anidados (dot.notation)
JettraAggregation agg3 = client.aggregate("pedidos")
        .groupBy("shipping.country", "shipping.city")
        .sum("payment.amount", "totalVentas");

// 4. Agregación global sin agrupación (totales globales)
JettraAggregation aggGlobal = client.aggregate("facturas")
        .count("totalFacturas")
        .sum("totalAmount", "facturacionGlobal")
        .avg("totalAmount", "ticketPromedioGlobal");
```

### 8.3 Funciones de Acumulación Disponibles

| Acumulador | Sobrecarga | Descripción |
| :--- | :--- | :--- |
| **`count`** | `count()`, `count(alias)` | Cuenta el número de documentos o filas en el grupo (alias por defecto: `"count"`). |
| **`sum`** | `sum(campo)`, `sum(campo, alias)` | Suma los valores numéricos del campo especificado. |
| **`avg`** | `avg(campo)`, `avg(campo, alias)` | Calcula la media aritmética con precisión redondeada a 2 decimales. |
| **`min`** | `min(campo)`, `min(campo, alias)` | Determina el valor numérico mínimo del grupo. |
| **`max`** | `max(campo)`, `max(campo, alias)` | Determina el valor numérico máximo del grupo. |

### 8.4 Filtrado Previo (`filter`) y Filtrado Posterior (`having`)

- **`filter(Predicate<JsonObject>)`**: Se ejecuta antes del agrupamiento, descartando documentos antes de entrar a la fase de acumulación (equivalente a `WHERE` en SQL o `$match` en MongoDB).
- **`having(Predicate<AggregationRow>)`**: Se ejecuta tras calcular las agregaciones, permitiendo filtrar grupos basándose en sus valores acumulados (equivalente a `HAVING` en SQL).

```java
AggregationResult resultado = client.aggregate("invoices")
        .filter(doc -> "ISSUED".equals(doc.get("status")))
        .groupBy("branchRef")
        .sum("totalAmount", "totalVentas")
        .count("cantidadFacturas")
        .having(row -> row.getDouble("totalVentas") >= 50000.0)
        .sortByDesc("totalVentas")
        .execute();
```

### 8.5 Enriquecimiento de Referencias Cruzadas (`lookup`)

Cuando un grupo se basa en una clave que contiene un puntero `jref://`, la operación `.lookup(refField, targetAlias)` resuelve automáticamente el puntero en O(1) e incrusta el documento referenciado dentro de la fila agregada:

```java
AggregationResult ventasVendedores = client.aggregate("invoices")
        .groupBy("sellerRef")
        .sum("totalAmount", "ventasTotales")
        .count("totalTickets")
        .lookup("sellerRef", "vendedor")
        .sortByDesc("ventasTotales")
        .execute();
```

---

## 9. Caso de Estudio Completo: Base de Datos Multi-Modelo `ExampleFactura`

`JettraDB` incluye la base de datos de demostración empresarial **`ExampleFactura`**, la cual demuestra la interoperabilidad real entre los 9 motores de almacenamiento, las consultas políglotas y la **conversión directa a Java 25 Records**:

```text
               Base de Datos Multi-Modelo: ExampleFactura
 ┌─────────────────────────────────────────────────────────────────────────┐
 │ 1. RECORDS    : Empresas (comp_*) | Vendedores (seller_*) | Productos   │
 │ 2. GEOSPATIAL : Sucursales comerciales con coordenadas GPS (sucursal_*) │
 │ 3. GRAPH      : Taxonomía jerárquica de categorías de producto (group_*)│
 │ 4. DOCUMENT   : Clientes corporativos y términos de pago (cust_*)       │
 │ 5. TIMESERIES : Inventario y balance de existencias (inv_metric_*)      │
 │ 6. COLUMN     : Facturas comerciales (fac_*) y Detalles (det_*)         │
 │ 7. OBJECT     : Facturas fiscales digitales en formato PDF (*.pdf)      │
 │ 8. VECTOR     : Embeddings semánticos para recomendaciones (vec_prod_*) │
 │ 9. KEYVALUE   : Sesiones activas de vendedores (seller_session_*)       │
 └─────────────────────────────────────────────────────────────────────────┘
```

A continuación se muestra un programa Java completo que demuestra cómo interactuar con `ExampleFactura` combinando operaciones CRUD, resolución de referencias cruzadas, agregaciones avanzadas y **conversión tipada a Java Records**:

```java
import com.jettra.driver.java.*;
import io.jettra.json.JettraJson;
import io.jettra.json.JsonObject;
import java.util.List;
import java.util.Optional;

public class ExampleFacturaDemo {

    // -----------------------------------------------------------------
    // Definición de Java 25 Records Tipados para ExampleFactura
    // -----------------------------------------------------------------
    public record FacturaModel(
        String invoiceId,
        String invoiceNumber,
        String fiscalDocNumber,
        String customerRef,
        String sellerRef,
        String branchRef,
        String pdfDocumentRef,
        double subtotal,
        double taxAmount,
        double totalAmount,
        String status
    ) {}

    public record ClienteModel(
        String id,
        String name,
        String taxId,
        String city,
        String customerType,
        double creditLimit,
        String preferredBranchRef,
        String status
    ) {}

    public record VendedorModel(
        String id,
        String code,
        String name,
        String email,
        double commissionPercent,
        String branchRef,
        String status
    ) {}

    public record SucursalModel(
        String id,
        String name,
        String code,
        double lat,
        double lon,
        String address,
        String status
    ) {}

    public record VentasPorSucursalRecord(
        String branchRef,
        long cantidadFacturas,
        double ventasTotales,
        double ticketPromedio,
        double ventaMinima,
        double ventaMaxima
    ) {}

    public record BalanceInventarioGlobal(
        long totalLineas,
        long existenciasTotales,
        double promedioPorProducto
    ) {}

    public static void main(String[] args) throws Exception {
        JettraClient client = new JettraClient("localhost", 8086);
        client.connect();
        client.login("admin", "admin");

        String db = "ExampleFactura";

        System.out.println("================================================================");
        System.out.println("   JettraDBDriverJava - Suite Completa: ExampleFactura         ");
        System.out.println("================================================================\n");

        // -------------------------------------------------------------
        // CASO 1: Consulta de Factura en COLUMN y Conversión a Record
        // -------------------------------------------------------------
        System.out.println("=== 1. Consultar Factura y Convertir a FacturaModel (Record) ===");
        String facRaw = client.getColumnRow(db, "fac_1");
        FacturaModel factura = client.toRecord(facRaw, FacturaModel.class);

        System.out.printf("Factura: %s | Fiscal: %s%n", factura.invoiceNumber(), factura.fiscalDocNumber());
        System.out.printf("Subtotal: $%.2f | Impuesto: $%.2f | Total: $%.2f%n",
            factura.subtotal(), factura.taxAmount(), factura.totalAmount());

        // -------------------------------------------------------------
        // CASO 2: Resolución O(1) de Referencias Directas a Records Tipados
        // -------------------------------------------------------------
        System.out.println("\n=== 2. Resolución de Punteros jref:// directamente a Java Records ===");
        Optional<ClienteModel> cliente = client.resolveRef(factura.customerRef(), ClienteModel.class);
        Optional<VendedorModel> vendedor = client.resolveRef(factura.sellerRef(), VendedorModel.class);
        Optional<SucursalModel> sucursal = client.resolveRef(factura.branchRef(), SucursalModel.class);

        cliente.ifPresent(c -> 
            System.out.printf("• Cliente (DOCUMENT):   %s | Ciudad: %s | Límite: $%.2f%n",
                c.name(), c.city(), c.creditLimit()));
        vendedor.ifPresent(v -> 
            System.out.printf("• Vendedor (RECORDS):   %s (%s) | Comisión: %.1f%%%n",
                v.name(), v.code(), v.commissionPercent()));
        sucursal.ifPresent(s -> 
            System.out.printf("• Sucursal (GEOSPATIAL): %s | GPS: [%.4f, %.4f] | Dir: %s%n",
                s.name(), s.lat(), s.lon(), s.address()));

        // -------------------------------------------------------------
        // CASO 3: Agregación de Facturas por Sucursal a Java Records
        // -------------------------------------------------------------
        System.out.println("\n=== 3. Agregación Analítica de Facturas y Mapeo a VentasPorSucursalRecord ===");
        List<String> facturasLote = List.of(
            "{\"branchRef\":\"jref://GEOSPATIAL:ExampleFactura/sucursal_1\",\"totalAmount\":1250.00,\"status\":\"ISSUED\"}",
            "{\"branchRef\":\"jref://GEOSPATIAL:ExampleFactura/sucursal_1\",\"totalAmount\":850.50,\"status\":\"ISSUED\"}",
            "{\"branchRef\":\"jref://GEOSPATIAL:ExampleFactura/sucursal_1\",\"totalAmount\":2100.00,\"status\":\"ISSUED\"}",
            "{\"branchRef\":\"jref://GEOSPATIAL:ExampleFactura/sucursal_2\",\"totalAmount\":3400.00,\"status\":\"ISSUED\"}",
            "{\"branchRef\":\"jref://GEOSPATIAL:ExampleFactura/sucursal_2\",\"totalAmount\":1600.00,\"status\":\"ISSUED\"}",
            "{\"branchRef\":\"jref://GEOSPATIAL:ExampleFactura/sucursal_3\",\"totalAmount\":450.00,\"status\":\"ISSUED\"}"
        );

        List<VentasPorSucursalRecord> ventasSucursales = client.aggregate(facturasLote)
                .groupBy("branchRef")
                .count("cantidadFacturas")
                .sum("totalAmount", "ventasTotales")
                .avg("totalAmount", "ticketPromedio")
                .min("totalAmount", "ventaMinima")
                .max("totalAmount", "ventaMaxima")
                .sortByDesc("ventasTotales")
                .execute(facturasLote)
                .toRecordList(VentasPorSucursalRecord.class);

        for (VentasPorSucursalRecord v : ventasSucursales) {
            System.out.printf("Sucursal: %s%n", v.branchRef());
            System.out.printf("  • Cantidad de Facturas: %d%n", v.cantidadFacturas());
            System.out.printf("  • Ventas Totales:       $%.2f%n", v.ventasTotales());
            System.out.printf("  • Ticket Promedio:      $%.2f%n", v.ticketPromedio());
            System.out.printf("  • Rango (Mín - Máx):    $%.2f - $%.2f%n%n", v.ventaMinima(), v.ventaMaxima());
        }

        // -------------------------------------------------------------
        // CASO 4: Agregación Global de Métricas de Inventario a Record
        // -------------------------------------------------------------
        System.out.println("=== 4. Agregación Global de Inventario a BalanceInventarioGlobal ===");
        List<String> inventarioMetricas = List.of(
            "{\"invId\":\"inv_metric_1\",\"quantityOnHand\":450,\"branchRef\":\"sucursal_1\"}",
            "{\"invId\":\"inv_metric_2\",\"quantityOnHand\":280,\"branchRef\":\"sucursal_1\"}",
            "{\"invId\":\"inv_metric_3\",\"quantityOnHand\":720,\"branchRef\":\"sucursal_2\"}",
            "{\"invId\":\"inv_metric_4\",\"quantityOnHand\":150,\"branchRef\":\"sucursal_3\"}"
        );

        Optional<BalanceInventarioGlobal> balanceOpt = client.aggregate(inventarioMetricas)
                .count("totalLineas")
                .sum("quantityOnHand", "existenciasTotales")
                .avg("quantityOnHand", "promedioPorProducto")
                .execute(inventarioMetricas)
                .getFirstAsRecord(BalanceInventarioGlobal.class);

        balanceOpt.ifPresent(b -> {
            System.out.printf("Líneas de Inventario Auditadas: %d%n", b.totalLineas());
            System.out.printf("Existencias Totales en Almacén: %d unidades%n", b.existenciasTotales());
            System.out.printf("Promedio de Unidades por Ítem:  %.2f unidades%n", b.promedioPorProducto());
        });

        client.close();
    }
}
```

---

## 10. Copias de Seguridad y Diagnóstico del Sistema

### 10.1 Ejecución de Backup Snapshot con Metadatos

```java
import com.jettra.driver.java.BackupResult;

// Ejecuta un snapshot en caliente y obtiene los detalles de almacenamiento
BackupResult resultado = client.triggerBackupDetailed();

if (resultado.success()) {
    System.out.println("✓ Snapshot completado exitosamente:");
    System.out.println("  • Archivo: " + resultado.fileName());
    System.out.println("  • Ruta:    " + resultado.path());
    System.out.println("  • Estado:  " + resultado.status());
} else {
    System.err.println("✗ Error al generar backup: " + resultado.errorMessage());
}
```

### 10.2 Métricas de Salud del Nodo

```java
String statusJson = client.getStatus();
System.out.println("Estado de hardware y red del nodo JettraDB:\n" + statusJson);
```

---

## 11. Resumen de Métodos Principales de `JettraClient`, `JettraRecordMapper` y la API de Agregación

### 11.1 Métodos de `JettraClient`

| Categoría | Método | Descripción |
| :--- | :--- | :--- |
| **Sesión** | `login(user, pass)` | Autentica y almacena el token Bearer JWT. |
| | `logout()` | Cierra la sesión activa. |
| | `changePassword(...)` | Actualiza la contraseña del usuario. |
| **Bases de Datos** | `listDatabases()` | Lista todas las particiones de base de datos activas. |
| | `createDatabase(db)` | Inicializa una nueva base de datos. |
| | `databaseExists(db)` | Comprueba si una base de datos existe. |
| | `dropDatabase(db)` | Elimina la base de datos y sus claves multi-modelo. |
| **Documentos** | `insertDocument(...)` | Inserta documento con ID manual o específico. |
| | `insertDocumentAuto(...)`| Inserta documento con ID automático (Sequence o UUID). |
| | `getDocument(col, id)` | Obtiene el documento JSON por ID. |
| | `listDocuments(col)` | Obtiene todos los documentos de una colección. |
| | `deleteDocument(col, id)`| Elimina el documento especificado. |
| | `getDocumentHistoryVersions(...)` | Obtiene el historial de versiones MVCC tipado. |
| | `restoreDocumentVersion(...)` | Restaura el documento a un timestamp anterior. |
| **Java Records** | `getRecord(col, id, class)` | Recupera un Java Record tipado. |
| | `getRecord(col, id, fields, class)` | Recupera un Java Record con proyección selectiva (`?fields=...`). |
| | `listRecords(col, class)` | Lista documentos convirtiéndolos directamente a `List<R>`. |
| | `query(col, filter, class)` | Consulta y filtra registros retornando `List<R>`. |
| | `toRecord(json / jsonObject, class)` | Transforma JSON directamente a Java Record. |
| | `resolveRef(ref, class)` | Resuelve puntero `jref://` directamente a Record tipado. |
| **Agregación** | `aggregate(collection)` | Inicializa el pipeline de agregación para una colección. |
| | `aggregate(model, col)` | Inicializa el pipeline para un motor y colección específicos. |
| | `aggregate(rawJsonDocs)` | Crea un pipeline en memoria sobre documentos JSON preexistentes. |
| **Multi-Model** | `putKeyValue / getKeyValue` | Operaciones en el motor KEYVALUE. |
| | `insertVector / getVector` | Operaciones con embeddings en el motor VECTOR. |
| | `addGraphNode / getGraphNode` | Operaciones en el motor GRAPH. |
| | `insertTimeSeries / getTimeSeries`| Métricas y telemetría en TIMESERIES. |
| | `insertColumnRow / getColumnRow` | Filas y familias en COLUMN. |
| | `insertLocation / getLocation` | Puntos GPS en GEOSPATIAL. |
| | `saveObject / getObject` | BLOBs y estados en OBJECT. |
| **Referencias** | `createRef(...)` | Construye una instancia `JettraReference`. |
| | `resolveRef(ref / uri / json)` | Resuelve el registro apuntado en tiempo O(1). |
| **Backups** | `triggerBackupDetailed()` | Ejecuta backup y reporta ruta y archivo resultante. |

### 11.2 Métodos de `JettraRecordMapper`

| Método | Argumentos | Descripción |
| :--- | :--- | :--- |
| `toRecord` | `String json, Class<R> clazz` | Mapea un string JSON a una instancia del Record `R`. |
| `toRecord` | `JsonObject jo, Class<R> clazz` | Mapea un `JsonObject` a una instancia del Record `R`. |
| `toRecord` | `AggregationRow row, Class<R> clazz`| Mapea una fila agregada a un Record de resumen analítico. |
| `toRecord` | `Map<String, Object> map, Class<R>` | Mapea un mapa clave-valor a un Record. |
| `toRecordList` | `List<?> items, Class<R> clazz` | Mapea una colección heterogénea a `List<R>`. |

### 11.3 Pipeline Fluent de `JettraAggregation`

| Método | Argumentos | Descripción |
| :--- | :--- | :--- |
| `groupBy` | `String... fields` / `List<String>` | Define las claves de agrupación (admite notación de punto `a.b`). |
| `count` | `[String alias]` | Cuenta elementos por grupo (alias por defecto `"count"`). |
| `sum` | `String field, [String alias]` | Suma numérica de la propiedad especificada. |
| `avg` | `String field, [String alias]` | Promedio aritmético con redondeo a 2 decimales. |
| `min` | `String field, [String alias]` | Valor mínimo encontrado en el grupo. |
| `max` | `String field, [String alias]` | Valor máximo encontrado en el grupo. |
| `filter` | `Predicate<JsonObject>` | Pre-filtro que descarta documentos antes de agrupar (`WHERE`). |
| `having` | `Predicate<AggregationRow>` | Post-filtro que descarta filas agregadas calculadas (`HAVING`). |
| `sortBy` | `String field, [boolean asc]` | Ordena resultados por clave o alias acumulado. |
| `sortByDesc` | `String field` | Orden descendente por la clave o alias indicado. |
| `skip` | `int offset` | Salta el número indicado de registros agregados. |
| `limit` | `int max` | Limita el número de filas en el resultado final. |
| `lookup` | `String refField, String alias`| Resuelve en O(1) la referencia `jref://` e incrusta la entidad. |
| `toRecordList` | `Class<R> recordClass` | Transforma directamente todas las filas agregadas en `List<R>`. |
| `getFirstAsRecord` | `Class<R> recordClass` | Obtiene la primera fila agregada convertida al Record `R`. |
| `execute` | `[List<String>] / [Collection<?>]` | Ejecuta el pipeline contra el servidor o sobre una colección en memoria. |
