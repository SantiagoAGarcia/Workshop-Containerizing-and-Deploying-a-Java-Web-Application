package co.edu.escuelaing.webframework;

import co.edu.escuelaing.app.Application;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class GracefulShutdownTest {

    private int testPort;
    private Thread serverThread;
    private HttpClient client;

    private void startServerWithApp(int shutdownTimeoutSeconds) throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            testPort = socket.getLocalPort();
        }

        WebFramework.reset();
        Application.initApp();

        serverThread = new Thread(() -> WebFramework.start(testPort, 4, shutdownTimeoutSeconds));
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
        assertTrue(ready, "Server failed to start in time for graceful shutdown test.");
    }

    @AfterEach
    void tearDown() {
        WebFramework.forceStop();
    }

    @Test
    @DisplayName("Graceful Shutdown: In-flight request drains and completes successfully when stop() is invoked")
    void testInFlightRequestDrainsSuccessfully() throws Exception {
        startServerWithApp(5);

        // 1. Launch a long-running request (1500 ms)
        CompletableFuture<HttpResponse<String>> slowRequestFuture = CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + testPort + "/sleep?ms=1500"))
                        .GET()
                        .build();
                return client.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // 2. Allow request to enter server execution
        Thread.sleep(300);

        // 3. Trigger graceful stop while the request is in flight
        WebFramework.stop();

        // 4. Verify that in-flight request receives complete 200 OK response
        HttpResponse<String> slowResponse = slowRequestFuture.get();
        assertEquals(200, slowResponse.statusCode());
        assertTrue(slowResponse.body().contains("Slept for 1500 ms"));

        // 5. Verify new connection attempts after stop() are rejected
        assertThrows(IOException.class, () -> {
            try (Socket s = new Socket("127.0.0.1", testPort)) {
                s.getOutputStream().write("GET /pi HTTP/1.1\r\n\r\n".getBytes());
            }
        });

        // 6. Verify server thread terminates cleanly
        serverThread.join(3000);
        assertFalse(serverThread.isAlive(), "Server thread should finish cleanly after draining.");
    }

    @Test
    @DisplayName("Graceful Shutdown: GET /shutdown returns 200 OK without deadlock and terminates server")
    void testShutdownEndpointFromClient() throws Exception {
        startServerWithApp(5);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/shutdown"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("shutting down"));

        // Wait for serverThread to exit cleanly
        serverThread.join(3000);
        assertFalse(serverThread.isAlive(), "Server thread must exit after /shutdown without self-deadlock.");
    }

    @Test
    @DisplayName("Graceful Shutdown: GET /shutdown returns 404 in production environment")
    void testShutdownEndpointDisabledInProduction() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            testPort = socket.getLocalPort();
        }

        WebFramework.reset();

        // Simulate APP_ENV=production by manually configuring routes as production would
        // (staticfiles, /hello, /pi, /env, without /shutdown)
        WebFramework.staticfiles("/webroot");
        WebFramework.get("/pi", (req, resp) -> {
            resp.type("text/plain; charset=utf-8");
            return String.valueOf(Math.PI);
        });
        // In production, /shutdown is NOT registered

        serverThread = new Thread(() -> WebFramework.start(testPort, 2, 5));
        serverThread.setDaemon(true);
        serverThread.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        // Wait for server ready
        boolean ready = false;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + testPort + "/pi"))
                        .GET()
                        .build();
                if (client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode() == 200) {
                    ready = true;
                    break;
                }
            } catch (Exception ignored) {}
        }
        assertTrue(ready);

        HttpRequest shutdownReq = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/shutdown"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(shutdownReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
        assertEquals("404 Not Found", response.body());
    }
}
