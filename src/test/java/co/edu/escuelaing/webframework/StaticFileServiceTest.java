package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StaticFileServiceTest {

    private StaticFileService service;

    @BeforeEach
    void setUp() {
        service = new StaticFileService();
        service.setStaticFolder("/webroot");
    }

    @Test
    @DisplayName("Should detect MIME types for supported file extensions")
    void testMimeTypeDetection() {
        assertEquals("text/html; charset=utf-8", service.getMimeType("/index.html"));
        assertEquals("text/css; charset=utf-8", service.getMimeType("/styles.css"));
        assertEquals("application/javascript; charset=utf-8", service.getMimeType("/app.js"));
        assertEquals("image/png", service.getMimeType("/images/logo.png"));
        assertEquals("image/jpeg", service.getMimeType("/photo.jpg"));
        assertEquals("image/jpeg", service.getMimeType("/photo.jpeg"));
        assertEquals("image/gif", service.getMimeType("/anim.gif"));
        assertEquals("image/svg+xml", service.getMimeType("/vector.svg"));
        assertEquals("image/x-icon", service.getMimeType("/favicon.ico"));
        assertEquals("application/json; charset=utf-8", service.getMimeType("/data.json"));
        assertEquals("text/plain; charset=utf-8", service.getMimeType("/notes.txt"));
        assertEquals("application/octet-stream", service.getMimeType("/unknown.xyz"));
    }

    @Test
    @DisplayName("Should detect and block path traversal attempts")
    void testPathTraversalDefense() {
        String[] maliciousPaths = {
                "/../secret.txt",
                "/../../etc/passwd",
                "/images/../../windows/system.ini",
                "/..\\windows\\system32",
                "/webroot/%2e%2e/config.json",
                "/dir//file.txt"
        };

        for (String malicious : maliciousPaths) {
            assertTrue(service.isPathTraversal(malicious), "Should flag as traversal: " + malicious);
            assertThrows(SecurityException.class, () -> service.resolve(malicious));
        }
    }

    @Test
    @DisplayName("Should resolve root path to index.html")
    void testRootResolvesToIndexHtml() {
        StaticFileService.StaticResource res = service.resolve("/");
        assertNotNull(res, "Root path should resolve to index.html");
        assertEquals("text/html; charset=utf-8", res.getContentType());
        assertTrue(res.getBytes().length > 0);
    }

    @Test
    @DisplayName("Should load binary PNG logo intact with magic bytes")
    void testLoadBinaryLogoIntact() {
        StaticFileService.StaticResource res = service.resolve("/images/logo.png");
        assertNotNull(res, "Logo should be resolved");
        assertEquals("image/png", res.getContentType());
        byte[] bytes = res.getBytes();
        assertTrue(bytes.length > 0);

        // Verify PNG magic header: 0x89 0x50 0x4E 0x47 ('\x89PNG')
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals((byte) 'P', bytes[1]);
        assertEquals((byte) 'N', bytes[2]);
        assertEquals((byte) 'G', bytes[3]);
    }

    @Test
    @DisplayName("Should return null for missing static resource")
    void testMissingStaticResourceReturnsNull() {
        StaticFileService.StaticResource res = service.resolve("/nonexistent-file.xyz");
        assertNull(res);
    }
}
