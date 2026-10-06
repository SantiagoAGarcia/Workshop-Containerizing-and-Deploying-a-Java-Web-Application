package co.edu.escuelaing.webframework;

import co.edu.escuelaing.app.Application;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebFrameworkIntegrationTest {

    private static int testPort;
    private static Thread serverThread;
    private static HttpClient client;

    @BeforeAll
    static void startServer() throws Exception {
        // Find an open ephemeral port
        try (ServerSocket socket = new ServerSocket(0)) {
            testPort = socket.getLocalPort();
        }

        WebFramework.reset();
        Application.initApp();

        serverThread = new Thread(() -> WebFramework.start(testPort));
        serverThread.setDaemon(true);
        serverThread.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        // Wait until server is accepting connections
        boolean ready = false;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + testPort + "/pi"))
                        .GET()
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    ready = true;
                    break;
                }
            } catch (Exception ignored) {
            }
        }
        assertTrue(ready, "Server failed to start in time for integration tests.");
    }

    @AfterAll
    static void stopServer() {
        WebFramework.forceStop();
    }

    @Test
    @Order(1)
    @DisplayName("Integration: GET /hello with parameter returns customized greeting")
    void testHelloWithParameter() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/hello?name=Pedro"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("Pedro"));
    }

    @Test
    @Order(2)
    @DisplayName("Integration: GET /hello without parameter returns default greeting")
    void testHelloWithoutParameter() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/hello"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("world"));
    }

    @Test
    @Order(3)
    @DisplayName("Integration: GET /pi returns Math.PI string representation")
    void testPiEndpoint() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/pi"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertEquals(String.valueOf(Math.PI), resp.body());
    }

    @Test
    @Order(4)
    @DisplayName("Integration: GET /env returns JSON environment status")
    void testEnvEndpoint() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/env"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.headers().firstValue("content-type").orElse("").contains("application/json"));
        assertTrue(resp.body().contains("\"appEnv\""));
        assertTrue(resp.body().contains("\"greetingPrefix\""));
    }

    @Test
    @Order(5)
    @DisplayName("Integration: GET / serves index.html static markup")
    void testRootServesIndexHtml() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.headers().firstValue("content-type").orElse("").contains("text/html"));
        assertTrue(resp.body().contains("Mini Web Framework"));
    }

    @Test
    @Order(6)
    @DisplayName("Integration: GET /styles.css serves CSS static stylesheet")
    void testStylesCss() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/styles.css"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.headers().firstValue("content-type").orElse("").contains("text/css"));
    }

    @Test
    @Order(7)
    @DisplayName("Integration: GET /app.js serves JavaScript script")
    void testAppJs() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/app.js"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.headers().firstValue("content-type").orElse("").contains("javascript"));
    }

    @Test
    @Order(8)
    @DisplayName("Integration: GET /images/logo.png serves intact binary PNG bytes")
    void testLogoPngBinaryBytes() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/images/logo.png"))
                .GET()
                .build();

        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, resp.statusCode());
        assertEquals("image/png", resp.headers().firstValue("content-type").orElse(""));
        byte[] bytes = resp.body();
        assertTrue(bytes.length > 0);
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals((byte) 'P', bytes[1]);
        assertEquals((byte) 'N', bytes[2]);
        assertEquals((byte) 'G', bytes[3]);
    }

    @Test
    @Order(9)
    @DisplayName("Integration: GET /unknown returns 404 Not Found")
    void testUnknownEndpoint404() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/unknown"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, resp.statusCode());
        assertEquals("404 Not Found", resp.body());
    }

    @Test
    @Order(10)
    @DisplayName("Integration: POST method returns 405 Method Not Allowed")
    void testMethodNotAllowed() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/pi"))
                .POST(HttpRequest.BodyPublishers.ofString("test"))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, resp.statusCode());
        assertTrue(resp.headers().firstValue("allow").orElse("").contains("GET"));
    }

    @Test
    @Order(11)
    @DisplayName("Integration: Malformed request line receives 400 Bad Request without crashing server")
    void testMalformedRequestLine() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", testPort);
             OutputStream out = socket.getOutputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            out.write("MALFORMED_LINE\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            String statusLine = reader.readLine();
            assertNotNull(statusLine);
            assertTrue(statusLine.contains("400 Bad Request"));
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/pi"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
    }

    @Test
    @Order(12)
    @DisplayName("Integration: Path traversal attempt receives 400 Bad Request")
    void testPathTraversalAttempt() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/..%2fsecret.txt"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Bad Request"));
    }

    @Test
    @Order(99)
    @DisplayName("Integration: GET /shutdown gracefully completes response and stops sequential server loop")
    void testGracefulShutdown() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/shutdown"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("shutting down"));

        // Wait up to 3 seconds for serverThread to exit cleanly
        serverThread.join(3000);
        assertFalse(serverThread.isAlive(), "Server thread should terminate after completing /shutdown.");
    }
}
