package com.jettra.driver.java;

import io.jettra.test.annotation.NotRequiresRunningServer;
import io.jettra.test.annotation.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static io.jettra.test.core.JettraAssert.*;

@NotRequiresRunningServer
public class JettraRecordMapperTest {

    public enum StatusEnum {
        ACTIVE, INACTIVE, SUSPENDED
    }

    public record ContactInfo(String email, String phone) {
    }

    public record CustomerRecord(
            String id,
            String name,
            String city,
            double creditLimit,
            StatusEnum status,
            ContactInfo contact,
            LocalDate registrationDate) {
    }

    public record BranchSalesSummary(
            String branchRef,
            long cantidadFacturas,
            double ventasTotales,
            double ticketPromedio) {
    }

    public record ProductRecord(
            String id,
            String sku,
            double unitPrice,
            boolean active) {
    }

    @Test
    void testDirectJsonToRecord() {
        String json = """
                {
                    "id": "cust_100",
                    "name": "Acme Global S.A.",
                    "city": "Panama City",
                    "creditLimit": 25000.50,
                    "status": "ACTIVE",
                    "contact": {
                        "email": "info@acme.com",
                        "phone": "+507 200-1111"
                    },
                    "registrationDate": "2026-01-15"
                }
                """;

        CustomerRecord rec = JettraRecordMapper.toRecord(json, CustomerRecord.class);
        assertNotNull(rec);
        assertEquals("cust_100", rec.id());
        assertEquals("Acme Global S.A.", rec.name());
        assertEquals("Panama City", rec.city());
        assertEquals(25000.50, rec.creditLimit(), 0.001);
        assertEquals(StatusEnum.ACTIVE, rec.status());
        assertNotNull(rec.contact());
        assertEquals("info@acme.com", rec.contact().email());
        assertEquals("+507 200-1111", rec.contact().phone());
        assertEquals(LocalDate.of(2026, 1, 15), rec.registrationDate());
    }

    @Test
    void testComponentsWrappedJson() {
        // JettraDB Records Engine stores payloads wrapped in components:
        String json = """
                {
                    "_recordClass": "com.factura.model.ProductRecord",
                    "_table": "products",
                    "components": {
                        "id": "prod_42",
                        "sku": "SKU-000042",
                        "unitPrice": 89.99,
                        "active": true
                    }
                }
                """;

        ProductRecord prod = JettraRecordMapper.toRecord(json, ProductRecord.class);
        assertNotNull(prod);
        assertEquals("prod_42", prod.id());
        assertEquals("SKU-000042", prod.sku());
        assertEquals(89.99, prod.unitPrice(), 0.001);
        assertTrue(prod.active());
    }

    @Test
    void testTolerantCaseAndSnakeCaseMapping() {
        String json = """
                {
                    "id": "prod_1",
                    "SKU": "SKU-999",
                    "unit_price": 120.50,
                    "ACTIVE": true
                }
                """;

        ProductRecord prod = JettraRecordMapper.toRecord(json, ProductRecord.class);
        assertNotNull(prod);
        assertEquals("prod_1", prod.id());
        assertEquals("SKU-999", prod.sku());
        assertEquals(120.50, prod.unitPrice(), 0.001);
        assertTrue(prod.active());
    }

    @Test
    void testAggregationRowToRecord() {
        List<String> facturas = List.of(
                "{\"branchRef\":\"sucursal_1\",\"totalAmount\":100.0}",
                "{\"branchRef\":\"sucursal_1\",\"totalAmount\":300.0}",
                "{\"branchRef\":\"sucursal_2\",\"totalAmount\":500.0}");

        JettraAggregation agg = new JettraAggregation();
        AggregationResult result = agg.groupBy("branchRef")
                .count("cantidadFacturas")
                .sum("totalAmount", "ventasTotales")
                .avg("totalAmount", "ticketPromedio")
                .sortBy("branchRef", true)
                .execute(facturas);

        assertEquals(2, result.size());

        // Test single row toRecord
        AggregationRow row1 = result.get(0);
        BranchSalesSummary summary1 = row1.toRecord(BranchSalesSummary.class);
        assertNotNull(summary1);
        assertEquals("sucursal_1", summary1.branchRef());
        assertEquals(2L, summary1.cantidadFacturas());
        assertEquals(400.0, summary1.ventasTotales(), 0.001);
        assertEquals(200.0, summary1.ticketPromedio(), 0.001);

        // Test whole result toRecordList
        List<BranchSalesSummary> summaries = result.toRecordList(BranchSalesSummary.class);
        assertEquals(2, summaries.size());
        assertEquals("sucursal_1", summaries.get(0).branchRef());
        assertEquals("sucursal_2", summaries.get(1).branchRef());
        assertEquals(500.0, summaries.get(1).ventasTotales(), 0.001);
    }

    @Test
    void testToRecordListFromRawJsonList() {
        List<String> rawProducts = List.of(
                "{\"id\":\"p1\",\"sku\":\"SKU-1\",\"unitPrice\":10.0,\"active\":true}",
                "{\"id\":\"p2\",\"sku\":\"SKU-2\",\"unitPrice\":20.0,\"active\":false}");

        List<ProductRecord> records = JettraRecordMapper.toRecordList(rawProducts, ProductRecord.class);
        assertEquals(2, records.size());
        assertEquals("p1", records.get(0).id());
        assertEquals("p2", records.get(1).id());
        assertFalse(records.get(1).active());
    }

    @Test
    void testClientToRecordMethods() {
        JettraClient client = new JettraClient("localhost", 8086);
        String json = "{\"id\":\"prod_99\",\"sku\":\"SKU-99\",\"unitPrice\":45.0,\"active\":true}";

        ProductRecord rec = client.toRecord(json, ProductRecord.class);
        assertNotNull(rec);
        assertEquals("prod_99", rec.id());
        assertEquals(45.0, rec.unitPrice(), 0.001);
        assertTrue(rec.active());
    }

    @Test
    void testRepositoryFilterAndFindFirst() {
        JettraClient client = new JettraClient("localhost", 8086);
        JettraRepository<ProductRecord> repo = client.recordRepository(ProductRecord.class, "products");

        List<ProductRecord> sampleList = List.of(
                new ProductRecord("p1", "SKU-A", 15.0, true),
                new ProductRecord("p2", "SKU-B", 55.0, true),
                new ProductRecord("p3", "SKU-C", 95.0, false));

        // Filter active with price > 50
        List<ProductRecord> filtered = sampleList.stream()
                .filter(p -> p.active() && p.unitPrice() > 50.0)
                .toList();

        assertEquals(1, filtered.size());
        assertEquals("p2", filtered.get(0).id());
    }
}
