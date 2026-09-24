package com.jettra.driver.java;

import io.jettra.test.annotation.NotRequiresRunningServer;
import io.jettra.test.annotation.Test;
import java.net.URI;
import java.util.Map;

import static io.jettra.test.core.JettraAssert.*;

/**
 * Verifies URI builder percent-encoding and safe character escaping in JettraClient.
 */
@NotRequiresRunningServer
public class JettraClientUriTest {

    @Test
    void testEncodePathSegment() {
        assertEquals("orders%20and%20invoices", JettraClient.encodePathSegment("orders and invoices"));
        assertEquals("c%C3%B3digo%2099", JettraClient.encodePathSegment("código 99"));
        assertEquals("item%2Fsku%231", JettraClient.encodePathSegment("item/sku#1"));
    }

    @Test
    void testBuildUriWithoutException() {
        JettraClient client = new JettraClient("127.0.0.1", 8080);

        String collection = "regional logistics & fleet";
        String docId = "hub/panama#01:east";
        String path = "/api/document/" + JettraClient.encodePathSegment(collection) + "/" + JettraClient.encodePathSegment(docId);
        Map<String, String> query = Map.of("id_mode", "manual", "tag", "env:prod & staging");

        assertDoesNotThrow(() -> {
            URI uri = client.buildUri(path, query);
            assertNotNull(uri);
            assertTrue(uri.toASCIIString().startsWith("http://127.0.0.1:8080/api/document/"));
            assertTrue(uri.toASCIIString().contains("regional%20logistics%20%26%20fleet"));
            assertTrue(uri.toASCIIString().contains("id_mode=manual"));
        });
    }
}
