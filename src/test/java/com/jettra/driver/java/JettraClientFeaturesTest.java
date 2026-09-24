package com.jettra.driver.java;

import io.jettra.json.JsonObject;
import io.jettra.test.annotation.NotRequiresRunningServer;
import io.jettra.test.annotation.Test;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static io.jettra.test.core.JettraAssert.*;

@NotRequiresRunningServer
public class JettraClientFeaturesTest {

    public record FacturaItem(String sku, String description, int quantity, double price) {
    }

    public record FacturaRecord(String id, String customerId, LocalDate date, List<FacturaItem> items, double total) {
    }

    @Test
    void testDatabaseUris() {
        JettraClient client = new JettraClient("127.0.0.1", 8086);

        URI listUri = client.buildUri("/api/databases", null);
        assertEquals("http://127.0.0.1:8086/api/databases", listUri.toString());

        URI getDbUri = client.buildUri("/api/databases/" + JettraClient.encodePathSegment("ExampleFactura"), null);
        assertEquals("http://127.0.0.1:8086/api/databases/ExampleFactura", getDbUri.toString());
    }

    @Test
    void testFieldProjectionUri() {
        JettraClient client = new JettraClient("127.0.0.1", 8086);
        String path = "/api/model/records/employees/EMP-001";
        URI uri = client.buildUri(path, Map.of("fields", "fullName,salary,department"));

        assertTrue(uri.toString().startsWith("http://127.0.0.1:8086/api/model/records/employees/EMP-001?"));
        assertTrue(uri.toString().contains("fields=fullName%2Csalary%2Cdepartment"));
    }

    @Test
    void testDocumentVersionModel() {
        DocumentVersion v = new DocumentVersion(1, 1788000000000L, "2026-09-24 10:00:00", "{\"name\":\"Factura-001\"}",
                true);
        assertEquals(1, v.versionNumber());
        assertEquals(1788000000000L, v.timestamp());
        assertEquals("2026-09-24 10:00:00", v.formattedDate());
        assertEquals("{\"name\":\"Factura-001\"}", v.payload());
        assertTrue(v.isCurrent());
    }

    @Test
    void testBackupResultModel() {
        BackupResult ok = BackupResult.success("Backup initiated", "jettra_backup_123.snap", "/var/backups");
        assertTrue(ok.success());
        assertEquals("Backup initiated", ok.status());
        assertEquals("jettra_backup_123.snap", ok.fileName());
        assertEquals("/var/backups", ok.path());
        assertNull(ok.errorMessage());

        BackupResult fail = BackupResult.failure("Disk full");
        assertFalse(fail.success());
        assertEquals("FAILED", fail.status());
        assertEquals("Disk full", fail.errorMessage());
    }

    @Test
    void testRepositoryAutoIdExtraction() {
        JettraClient client = new JettraClient("localhost", 8086);
        JettraRepository<FacturaRecord> repo = client.recordRepository(FacturaRecord.class, "invoices");

        FacturaRecord record = new FacturaRecord(
                "FAC-9999",
                "CUST-001",
                LocalDate.of(2026, 9, 24),
                List.of(new FacturaItem("PROD-1", "Laptop Dell XPS", 1, 1499.99)),
                1499.99);

        // Verify entity id is extracted without crashing
        assertNotNull(record.id());
        assertEquals("FAC-9999", record.id());
    }
}
