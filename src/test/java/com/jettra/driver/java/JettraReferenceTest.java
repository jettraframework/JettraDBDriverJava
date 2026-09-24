package com.jettra.driver.java;

import io.jettra.json.JsonObject;
import io.jettra.test.annotation.NotRequiresRunningServer;
import io.jettra.test.annotation.Test;

import static io.jettra.test.core.JettraAssert.*;

@NotRequiresRunningServer
public class JettraReferenceTest {

    @Test
    void testComputeDirectStorageKeyUsesDocPrefixForDocument() {
        String key = JettraReference.computeDirectStorageKey("DOCUMENT", "ExampleFactura", "cust_101");
        assertEquals("doc:ExampleFactura:cust_101", key);

        String keyNull = JettraReference.computeDirectStorageKey(null, "ExampleFactura", "cust_101");
        assertEquals("doc:ExampleFactura:cust_101", keyNull);

        String keyRecords = JettraReference.computeDirectStorageKey("RECORDS", "ExampleFactura", "comp_1");
        assertEquals("rec:ExampleFactura:comp_1", keyRecords);

        String keyGeo = JettraReference.computeDirectStorageKey("GEOSPATIAL", "ExampleFactura", "sucursal_1");
        assertEquals("geo:ExampleFactura:sucursal_1", keyGeo);

        String keyVec = JettraReference.computeDirectStorageKey("VECTOR", "ExampleFactura", "vec_1");
        assertEquals("vec:ExampleFactura:vec_1", keyVec);

        String keyCol = JettraReference.computeDirectStorageKey("COLUMN", "ExampleFactura", "fac_1");
        assertEquals("col:ExampleFactura:fac_1", keyCol);
    }

    @Test
    void testParseStandardUris() {
        JettraReference ref1 = JettraReference.parse("jref://RECORDS:ExampleFactura/comp_1");
        assertNotNull(ref1);
        assertEquals("RECORDS", ref1.engine());
        assertEquals("ExampleFactura", ref1.database());
        assertEquals("comp_1", ref1.entityId());
        assertEquals("rec:ExampleFactura:comp_1", ref1.directStorageKey());

        JettraReference ref2 = JettraReference.parse("jref://node-01@VECTOR:ai_db/face_emb_42");
        assertNotNull(ref2);
        assertEquals("node-01", ref2.node());
        assertEquals("VECTOR", ref2.engine());
        assertEquals("ai_db", ref2.database());
        assertEquals("face_emb_42", ref2.entityId());
    }

    @Test
    void testParseJsonWithJref() {
        String json = "{\"$jref\":\"jref://GEOSPATIAL:ExampleFactura/sucursal_12\"}";
        assertTrue(JettraReference.isReference(json));

        JettraReference ref = JettraReference.parse(json);
        assertNotNull(ref);
        assertEquals("GEOSPATIAL", ref.engine());
        assertEquals("ExampleFactura", ref.database());
        assertEquals("sucursal_12", ref.entityId());
    }

    @Test
    void testToJsonObject() {
        JettraReference ref = JettraReference.of("COLUMN", "ExampleFactura", "fac_500");
        JsonObject jo = ref.toJsonObject();
        assertNotNull(jo);
        assertTrue(jo.has("$jref"));
        assertEquals("jref://COLUMN:ExampleFactura/fac_500", jo.get("$jref"));
    }

    @Test
    void testParseQueryParamWrapper() {
        String wrapped = "/engines?action=resolve_ref&uri=jref%3A%2F%2FCOLUMN%3AExampleFactura%2Ffac_99";
        JettraReference ref = JettraReference.parse(wrapped);
        assertNotNull(ref);
        assertEquals("COLUMN", ref.engine());
        assertEquals("ExampleFactura", ref.database());
        assertEquals("fac_99", ref.entityId());
    }
}
