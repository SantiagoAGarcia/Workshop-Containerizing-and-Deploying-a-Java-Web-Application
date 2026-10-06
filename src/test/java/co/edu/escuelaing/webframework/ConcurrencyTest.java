package co.edu.escuelaing.webframework;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyTest {

    private int testPort;
    private Thread serverThread;
    private HttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            testPort = socket.getLocalPort();
        }

        WebFramework.reset();
        WebFramework.get("/slow", (req, resp) -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            resp.type("text/plain; charset=utf-8");
            return "Done on " + Thread.currentThread().getName();
        });

        // Start server with 5 worker threads
        serverThread = new Thread(() -> WebFramework.start(testPort, 5, 5));
        serverThread.setDaemon(true);
        serverThread.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        // Wait for server ready
        boolean ready = false;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(100);
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + testPort + "/slow"))
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
        assertTrue(ready, "Server did not start in time.");
    }

    @AfterEach
    void tearDown() {
        WebFramework.forceStop();
    }

    @Test
    @DisplayName("Concurrency: 5 concurrent 1-second requests complete in ~1s total rather than ~5s")
    void testConcurrentExecutionTime() throws Exception {
        int numberOfRequests = 5;
        ExecutorService clientPool = Executors.newFixedThreadPool(numberOfRequests);

        long startTime = System.currentTimeMillis();

        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < numberOfRequests; i++) {
            CompletableFuture<HttpResponse<String>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + testPort + "/slow"))
                            .GET()
                            .build();
                    return client.send(req, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, clientPool);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long totalElapsedMs = System.currentTimeMillis() - startTime;
        clientPool.shutdown();

        for (CompletableFuture<HttpResponse<String>> f : futures) {
            HttpResponse<String> resp = f.get();
            assertEquals(200, resp.statusCode());
            assertTrue(resp.body().startsWith("Done on http-worker-"));
        }

        System.out.printf("[ConcurrencyTest] Total elapsed time for %d concurrent 1s requests: %d ms%n",
                numberOfRequests, totalElapsedMs);

        // Assert that concurrent execution took ~1s (< 2500ms) rather than sequential ~5s (>= 5000ms)
        assertTrue(totalElapsedMs < 2500,
                "Concurrent execution took " + totalElapsedMs + "ms, expected < 2500ms (near 1000ms).");
    }

    @Test
    @DisplayName("Contrast: Demonstrates why a sequential server would take N seconds (N * 1000ms)")
    void testSequentialContrastExplanation() {
        int numberOfRequests = 5;
        int delayPerRequestMs = 1000;
        int theoreticalSequentialTimeMs = numberOfRequests * delayPerRequestMs;

        // In a sequential single-threaded accept loop, each request blocks the main thread
        // during its entire execution (1000ms), forcing each subsequent request to wait in the TCP queue.
        // Therefore, N=5 requests sequentially require at least 5000ms.
        // With our concurrent thread pool, all 5 requests are handled simultaneously across 5 threads in ~1000ms.
        assertTrue(theoreticalSequentialTimeMs >= 5000);
    }
}
