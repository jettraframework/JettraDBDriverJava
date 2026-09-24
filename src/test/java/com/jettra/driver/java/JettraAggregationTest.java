package com.jettra.driver.java;

import io.jettra.test.annotation.NotRequiresRunningServer;
import io.jettra.test.annotation.Test;
import java.util.List;
import static io.jettra.test.core.JettraAssert.*;

@NotRequiresRunningServer
public class JettraAggregationTest {

    record CustomerRecord(String id, String name, String city, String customerType, double creditLimit, String status) {}
    record InvoiceRecord(String id, String branchRef, String sellerRef, double totalAmount, String status) {}

    @Test
    void testGroupBySingleFieldAndCount() {
        List<String> customers = List.of(
            "{\"id\":\"cust_1\",\"name\":\"Empresa 1\",\"city\":\"Panamá\",\"customerType\":\"CORPORATE\",\"creditLimit\":10000.0,\"status\":\"ACTIVE\"}",
            "{\"id\":\"cust_2\",\"name\":\"Empresa 2\",\"city\":\"Panamá\",\"customerType\":\"SMB\",\"creditLimit\":5000.0,\"status\":\"ACTIVE\"}",
            "{\"id\":\"cust_3\",\"name\":\"Empresa 3\",\"city\":\"David\",\"customerType\":\"RETAIL\",\"creditLimit\":3000.0,\"status\":\"ACTIVE\"}",
            "{\"id\":\"cust_4\",\"name\":\"Empresa 4\",\"city\":\"David\",\"customerType\":\"CORPORATE\",\"creditLimit\":15000.0,\"status\":\"ACTIVE\"}",
            "{\"id\":\"cust_5\",\"name\":\"Empresa 5\",\"city\":\"Panamá\",\"customerType\":\"CORPORATE\",\"creditLimit\":8000.0,\"status\":\"INACTIVE\"}"
        );

        JettraAggregation agg = new JettraAggregation();
        AggregationResult result = agg.groupBy("city")
                .count("totalCustomers")
                .sortBy("city", true)
                .execute(customers);

        assertNotNull(result);
        assertEquals(2, result.size());

        // First row: David
        AggregationRow davidRow = result.get(0);
        assertEquals("David", davidRow.getString("city"));
        assertEquals(2L, davidRow.getLong("totalCustomers"));

        // Second row: Panamá
        AggregationRow panamaRow = result.get(1);
        assertEquals("Panamá", panamaRow.getString("city"));
        assertEquals(3L, panamaRow.getLong("totalCustomers"));
    }

    @Test
    void testSumAvgMinMaxAccumulators() {
        List<String> invoices = List.of(
            "{\"invoiceId\":\"fac_1\",\"branchRef\":\"sucursal_1\",\"totalAmount\":100.0,\"status\":\"ISSUED\"}",
            "{\"invoiceId\":\"fac_2\",\"branchRef\":\"sucursal_1\",\"totalAmount\":200.0,\"status\":\"ISSUED\"}",
            "{\"invoiceId\":\"fac_3\",\"branchRef\":\"sucursal_1\",\"totalAmount\":300.0,\"status\":\"ISSUED\"}",
            "{\"invoiceId\":\"fac_4\",\"branchRef\":\"sucursal_2\",\"totalAmount\":500.0,\"status\":\"ISSUED\"}"
        );

        JettraAggregation agg = new JettraAggregation();
        AggregationResult result = agg.groupBy("branchRef")
                .count("numInvoices")
                .sum("totalAmount", "totalSales")
                .avg("totalAmount", "avgTicket")
                .min("totalAmount", "minTicket")
                .max("totalAmount", "maxTicket")
                .sortBy("branchRef", true)
                .execute(invoices);

        assertEquals(2, result.size());

        AggregationRow suc1 = result.get(0);
        assertEquals("sucursal_1", suc1.getString("branchRef"));
        assertEquals(3L, suc1.getLong("numInvoices"));
        assertEquals(600.0, suc1.getDouble("totalSales"), 0.001);
        assertEquals(200.0, suc1.getDouble("avgTicket"), 0.001);
        assertEquals(100.0, suc1.getDouble("minTicket"), 0.001);
        assertEquals(300.0, suc1.getDouble("maxTicket"), 0.001);

        AggregationRow suc2 = result.get(1);
        assertEquals("sucursal_2", suc2.getString("branchRef"));
        assertEquals(1L, suc2.getLong("numInvoices"));
        assertEquals(500.0, suc2.getDouble("totalSales"), 0.001);
    }

    @Test
    void testPreFilterAndHaving() {
        List<String> invoices = List.of(
            "{\"invoiceId\":\"fac_1\",\"branchRef\":\"sucursal_1\",\"totalAmount\":100.0,\"status\":\"ISSUED\"}",
            "{\"invoiceId\":\"fac_2\",\"branchRef\":\"sucursal_1\",\"totalAmount\":200.0,\"status\":\"CANCELLED\"}",
            "{\"invoiceId\":\"fac_3\",\"branchRef\":\"sucursal_1\",\"totalAmount\":300.0,\"status\":\"ISSUED\"}",
            "{\"invoiceId\":\"fac_4\",\"branchRef\":\"sucursal_2\",\"totalAmount\":50.0,\"status\":\"ISSUED\"}",
            "{\"invoiceId\":\"fac_5\",\"branchRef\":\"sucursal_3\",\"totalAmount\":1000.0,\"status\":\"ISSUED\"}"
        );

        // Pre-filter: only ISSUED status
        // Having: totalSales >= 200.0
        JettraAggregation agg = new JettraAggregation();
        AggregationResult result = agg.groupBy("branchRef")
                .filter(doc -> "ISSUED".equals(doc.get("status")))
                .sum("totalAmount", "totalSales")
                .count("qty")
                .having(row -> row.getDouble("totalSales") >= 200.0)
                .sortByDesc("totalSales")
                .execute(invoices);

        // sucursal_3: 1000.0, sucursal_1: 400.0 (fac_2 was CANCELLED, so only fac_1 and fac_3 = 400.0)
        // sucursal_2: 50.0 (excluded by HAVING)
        assertEquals(2, result.size());
        assertEquals("sucursal_3", result.get(0).getString("branchRef"));
        assertEquals(1000.0, result.get(0).getDouble("totalSales"), 0.001);

        assertEquals("sucursal_1", result.get(1).getString("branchRef"));
        assertEquals(400.0, result.get(1).getDouble("totalSales"), 0.001);
        assertEquals(2L, result.get(1).getLong("qty"));
    }

    @Test
    void testCompositeMultiFieldGroupBy() {
        List<String> customers = List.of(
            "{\"city\":\"Panamá\",\"type\":\"CORPORATE\",\"credit\":50000.0}",
            "{\"city\":\"Panamá\",\"type\":\"CORPORATE\",\"credit\":30000.0}",
            "{\"city\":\"Panamá\",\"type\":\"RETAIL\",\"credit\":5000.0}",
            "{\"city\":\"David\",\"type\":\"CORPORATE\",\"credit\":20000.0}"
        );

        JettraAggregation agg = new JettraAggregation();
        AggregationResult result = agg.groupBy("city", "type")
                .count()
                .sum("credit")
                .sortBy("city", true)
                .sortBy("type", true)
                .execute(customers);

        assertEquals(3, result.size());
    }

    @Test
    void testNestedFieldAggregation() {
        List<String> orders = List.of(
            "{\"id\":\"ord_1\",\"shipping\":{\"country\":\"PA\",\"city\":\"Panamá\"},\"payment\":{\"amount\":150.0}}",
            "{\"id\":\"ord_2\",\"shipping\":{\"country\":\"PA\",\"city\":\"Panamá\"},\"payment\":{\"amount\":250.0}}",
            "{\"id\":\"ord_3\",\"shipping\":{\"country\":\"PA\",\"city\":\"David\"},\"payment\":{\"amount\":400.0}}"
        );

        JettraAggregation agg = new JettraAggregation();
        AggregationResult result = agg.groupBy("shipping.city")
                .sum("payment.amount", "totalCitySales")
                .count()
                .sortBy("shipping.city", true)
                .execute(orders);

        assertEquals(2, result.size());
        assertEquals("David", result.get(0).getString("shipping.city"));
        assertEquals(400.0, result.get(0).getDouble("totalCitySales"), 0.001);

        assertEquals("Panamá", result.get(1).getString("shipping.city"));
        assertEquals(400.0, result.get(1).getDouble("totalCitySales"), 0.001);
    }

    @Test
    void testExecutionFromJavaRecords() {
        List<CustomerRecord> list = List.of(
            new CustomerRecord("c1", "Empresa A", "Colón", "SMB", 7000.0, "ACTIVE"),
            new CustomerRecord("c2", "Empresa B", "Colón", "SMB", 8000.0, "ACTIVE"),
            new CustomerRecord("c3", "Empresa C", "Santiago", "CORPORATE", 15000.0, "ACTIVE")
        );

        JettraAggregation agg = new JettraAggregation();
        AggregationResult result = agg.groupBy("city")
                .avg("creditLimit", "avgCredit")
                .count()
                .sortByDesc("avgCredit")
                .execute(list);

        assertEquals(2, result.size());
        assertEquals("Santiago", result.get(0).getString("city"));
        assertEquals(15000.0, result.get(0).getDouble("avgCredit"), 0.001);

        assertEquals("Colón", result.get(1).getString("city"));
        assertEquals(7500.0, result.get(1).getDouble("avgCredit"), 0.001);
    }

    @Test
    void testPaginationSkipAndLimit() {
        List<String> items = List.of(
            "{\"category\":\"A\",\"val\":10}",
            "{\"category\":\"B\",\"val\":20}",
            "{\"category\":\"C\",\"val\":30}",
            "{\"category\":\"D\",\"val\":40}",
            "{\"category\":\"E\",\"val\":50}"
        );

        JettraAggregation agg = new JettraAggregation();
        AggregationResult result = agg.groupBy("category")
                .sum("val", "total")
                .sortBy("category", true)
                .skip(1)
                .limit(2)
                .execute(items);

        assertEquals(2, result.size());
        assertEquals("B", result.get(0).getString("category"));
        assertEquals("C", result.get(1).getString("category"));
    }

    @Test
    void testGlobalAggregationWithoutGroupBy() {
        List<String> items = List.of(
            "{\"amount\":100.0}",
            "{\"amount\":200.0}",
            "{\"amount\":300.0}"
        );

        JettraAggregation agg = new JettraAggregation();
        AggregationResult result = agg
                .count("totalRecords")
                .sum("amount", "totalSum")
                .avg("amount", "average")
                .min("amount", "minVal")
                .max("amount", "maxVal")
                .execute(items);

        assertEquals(1, result.size());
        AggregationRow row = result.get(0);
        assertEquals(3L, row.getLong("totalRecords"));
        assertEquals(600.0, row.getDouble("totalSum"), 0.001);
        assertEquals(200.0, row.getDouble("average"), 0.001);
        assertEquals(100.0, row.getDouble("minVal"), 0.001);
        assertEquals(300.0, row.getDouble("maxVal"), 0.001);
    }

    @Test
    void testToJsonAndSerialization() {
        List<String> items = List.of(
            "{\"department\":\"IT\",\"salary\":5000.0}",
            "{\"department\":\"IT\",\"salary\":6000.0}"
        );

        JettraAggregation agg = new JettraAggregation();
        AggregationResult result = agg.groupBy("department")
                .sum("salary", "totalSalary")
                .count()
                .execute(items);

        assertEquals(1, result.size());
        String json = result.toJson();
        assertNotNull(json);
        assertTrue(json.contains("totalSalary"));
        assertTrue(json.contains("11000"));
        assertTrue(json.contains("department"));
    }
}
