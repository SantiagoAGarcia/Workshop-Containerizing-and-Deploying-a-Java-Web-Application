package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaturationTest {

    private int testPort;
    private Thread serverThread;
    private HttpClient client;

    @AfterEach
    void tearDown() {
        WebFramework.forceStop();
    }

    @Test
    @DisplayName("Saturation: Overloaded server rejects surplus requests with HTTP 503 and remains healthy")
    void testSaturationReturns503() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            testPort = socket.getLocalPort();
        }

        CountDownLatch blockerLatch = new CountDownLatch(1);
        CountDownLatch activeWorkersLatch = new CountDownLatch(2);

        WebFramework.reset();
        WebFramework.get("/hold", (req, resp) -> {
            activeWorkersLatch.countDown();
            try {
                blockerLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            resp.type("text/plain; charset=utf-8");
            return "Released";
        });

        WebFramework.get("/ping", (req, resp) -> {
            resp.type("text/plain; charset=utf-8");
            return "pong";
        });

        // Thread pool size: 2 threads. Queue size in HttpServer will be max(10, 4) = 10.
        // Total capacity before rejection = 2 + 10 = 12 requests.
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
                        .uri(URI.create("http://127.0.0.1:" + testPort + "/ping"))
                        .GET()
                        .build();
                if (client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode() == 200) {
                    ready = true;
                    break;
                }
            } catch (Exception ignored) {}
        }
        assertTrue(ready);

        // Fill the 2 worker threads + 10 queue slots = 12 holding requests + 5 surplus requests = 17 total
        int totalBursts = 17;
        ExecutorService clientPool = Executors.newFixedThreadPool(totalBursts);
        AtomicInteger count503 = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < totalBursts; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try (Socket s = new Socket("127.0.0.1", testPort);
                     OutputStream out = s.getOutputStream();
                     BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {

                    s.setSoTimeout(4000);
                    out.write("GET /hold HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();

                    String statusLine = in.readLine();
                    if (statusLine != null && statusLine.contains("503")) {
                        count503.incrementAndGet();
                    }
                } catch (Exception ignored) {
                }
            }, clientPool));
            // Small pause between spawns to allow TCP acceptor to saturate worker pool & queue
            Thread.sleep(20);
        }

        // Release the blocking handlers
        blockerLatch.countDown();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        clientPool.shutdown();

        System.out.printf("[SaturationTest] Received %d HTTP 503 responses for surplus requests.%n", count503.get());
        assertTrue(count503.get() > 0, "Server must reject surplus requests with HTTP 503 when pool is saturated.");

        // Verify server is still alive and responsive after saturation clears
        HttpRequest pingReq = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + testPort + "/ping"))
                .GET()
                .build();
        HttpResponse<String> pingResp = client.send(pingReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, pingResp.statusCode());
        assertEquals("pong", pingResp.body());
    }
}
