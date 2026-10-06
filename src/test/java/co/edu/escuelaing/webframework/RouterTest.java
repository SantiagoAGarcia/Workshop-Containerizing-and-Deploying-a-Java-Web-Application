package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouterTest {

    private Router router;

    @BeforeEach
    void setUp() {
        router = new Router();
    }

    @Test
    @DisplayName("Should register and resolve existing dynamic route")
    void testRegisterAndResolveRoute() throws Exception {
        router.addRoute("GET", "/greeting", (req, resp) -> "Hello World");

        RouteHandler handler = router.getHandler("GET", "/greeting");
        assertNotNull(handler);

        Response resp = new Response();
        Request req = new Request("GET", "/greeting", null);
        String body = handler.handle(req, resp);
        assertEquals("Hello World", body);
    }

    @Test
    @DisplayName("Should return null for nonexistent route")
    void testNonexistentRouteReturnsNull() {
        RouteHandler handler = router.getHandler("GET", "/unknown");
        assertNull(handler);
        assertFalse(router.hasRoute("GET", "/unknown"));
    }

    @Test
    @DisplayName("Should differentiate HTTP methods for the same path")
    void testMethodDifferentiation() {
        router.addRoute("GET", "/resource", (req, resp) -> "GET response");

        assertNotNull(router.getHandler("GET", "/resource"));
        assertNull(router.getHandler("POST", "/resource"));
        assertTrue(router.hasPathAnyMethod("/resource"));
    }

    @Test
    @DisplayName("Should normalize leading slash in route path")
    void testNormalizeLeadingSlash() {
        router.addRoute("GET", "status", (req, resp) -> "OK");

        assertNotNull(router.getHandler("GET", "/status"));
        assertNotNull(router.getHandler("GET", "status"));
    }
}
