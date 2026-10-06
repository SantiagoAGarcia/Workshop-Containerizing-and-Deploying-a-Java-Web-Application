package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequestTest {

    @Test
    @DisplayName("Should parse method, path, and raw query correctly")
    void testBasicRequestParsing() {
        Request req = new Request("GET", "/hello?name=Pedro&language=es", Collections.emptyMap());

        assertEquals("GET", req.getMethod());
        assertEquals("/hello", req.getPath());
        assertEquals("name=Pedro&language=es", req.getRawQuery());
        assertEquals("Pedro", req.getValue("name"));
        assertEquals("es", req.getValue("language"));
    }

    @Test
    @DisplayName("Should decode URL-encoded query parameters properly")
    void testUrlDecoding() {
        Request req = new Request("GET", "/search?query=Java%2017%20Programming&author=Santiago%20Garc%C3%ADa", Collections.emptyMap());

        assertEquals("Java 17 Programming", req.getValue("query"));
        assertEquals("Santiago García", req.getValue("author"));
    }

    @Test
    @DisplayName("Should return null for missing query parameters without throwing exceptions")
    void testMissingParameterReturnsNull() {
        Request req = new Request("GET", "/hello?name=Pedro", Collections.emptyMap());

        assertNull(req.getValue("nonexistent"));
        assertNull(req.getValue(null));
    }

    @Test
    @DisplayName("Should handle parameter keys without values and repeated keys")
    void testKeysWithoutValuesAndRepeatedKeys() {
        Request req = new Request("GET", "/items?flag&tag=alpha&tag=beta&empty=", Collections.emptyMap());

        assertEquals("", req.getValue("flag"));
        assertEquals("", req.getValue("empty"));
        assertEquals("alpha", req.getValue("tag"));

        List<String> tags = req.getValues("tag");
        assertEquals(2, tags.size());
        assertEquals("alpha", tags.get(0));
        assertEquals("beta", tags.get(1));
    }

    @Test
    @DisplayName("Should normalize root and empty paths")
    void testNormalizePath() {
        Request req1 = new Request("GET", "", Collections.emptyMap());
        assertEquals("/", req1.getPath());

        Request req2 = new Request("GET", "api/status", Collections.emptyMap());
        assertEquals("/api/status", req2.getPath());
    }
}
