package co.edu.escuelaing.webframework;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrent HTTP/1.1 Server.
 * Supports thread pool concurrency, 503 saturation backpressure,
 * connection draining on graceful shutdown, and JVM SIGTERM shutdown hooks.
 */
public class HttpServer {

    private final Router router;
    private final StaticFileService staticFileService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private int boundPort = 8080;
    private int threadPoolSize = Runtime.getRuntime().availableProcessors() * 2;
    private int shutdownTimeoutSeconds = 10;
    private ThreadPoolExecutor executor;
    private Thread shutdownHookThread;

    public HttpServer(Router router, StaticFileService staticFileService) {
        this.router = router;
        this.staticFileService = staticFileService;
    }

    public HttpServer(Router router, StaticFileService staticFileService, int threadPoolSize, int shutdownTimeoutSeconds) {
        this.router = router;
        this.staticFileService = staticFileService;
        this.threadPoolSize = threadPoolSize > 0 ? threadPoolSize : Runtime.getRuntime().availableProcessors() * 2;
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds > 0 ? shutdownTimeoutSeconds : 10;
    }

    /**
     * Starts listening on the specified port. Binds to 0.0.0.0.
     *
     * @param port TCP port to bind to
     */
    public void start(int port) {
        this.boundPort = port;
        this.running.set(true);

        // Initialize bounded ThreadPoolExecutor
        int queueCapacity = Math.max(10, this.threadPoolSize * 2);
        this.executor = new ThreadPoolExecutor(
                this.threadPoolSize,
                this.threadPoolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new WorkerThreadFactory("http-worker-"),
                new SaturationRejectionHandler()
        );

        // Register SIGTERM shutdown hook for container and OS termination signals
        registerShutdownHook();

        System.out.println("===============================================================");
        System.out.println("  Mini Web Framework Concurrent HTTP Server");
        System.out.println("  Port: " + port + " | Bound to: 0.0.0.0 (All network interfaces)");
        System.out.println("  ThreadPool Size: " + this.threadPoolSize + " | Queue: " + queueCapacity);
        System.out.println("  Shutdown Timeout: " + this.shutdownTimeoutSeconds + "s");
        System.out.println("===============================================================");

        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("0.0.0.0", port));
            this.boundPort = serverSocket.getLocalPort();
            System.out.println("Server is listening on port " + this.boundPort + ". Ready for requests.");

            while (running.get()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    if (!running.get()) {
                        try { clientSocket.close(); } catch (IOException ignored) {}
                        break;
                    }
                    executor.execute(new ConnectionTask(clientSocket));
                } catch (SocketTimeoutException ignored) {
                } catch (IOException e) {
                    if (!running.get()) {
                        break;
                    }
                    System.err.println("Accept error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                System.err.println("Fatal server socket error: " + e.getMessage());
            }
        } finally {
            closeServerSocket();
            drainAndShutdownExecutor();
            removeShutdownHook();
            System.out.println("Server stopped gracefully.");
        }
    }

    /**
     * Signals server to stop accepting new requests and closes ServerSocket.
     * Draining and awaitTermination are safely carried out by the main loop in start().
     */
    public void stop() {
        if (this.running.compareAndSet(true, false)) {
            closeServerSocket();
        }
    }

    /**
     * Forcibly closes socket and terminates pool immediately.
     */
    public synchronized void forceStop() {
        this.running.set(false);
        closeServerSocket();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        removeShutdownHook();
    }

    private synchronized void closeServerSocket() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void drainAndShutdownExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(shutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                    System.err.println("Warning: Thread pool did not terminate within " + shutdownTimeoutSeconds + "s. Forcing shutdownNow.");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private synchronized void registerShutdownHook() {
        try {
            shutdownHookThread = new Thread(() -> {
                System.out.println("[ShutdownHook] Caught SIGTERM / JVM termination signal. Initiating graceful shutdown...");
                stop();
                drainAndShutdownExecutor();
            }, "http-shutdown-hook");
            Runtime.getRuntime().addShutdownHook(shutdownHookThread);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down
        }
    }

    private synchronized void removeShutdownHook() {
        if (shutdownHookThread != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHookThread);
            } catch (IllegalStateException ignored) {
            }
            shutdownHookThread = null;
        }
    }

    private final class ConnectionTask implements Runnable {
        private final Socket clientSocket;

        ConnectionTask(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public Socket getSocket() {
            return clientSocket;
        }

        @Override
        public void run() {
            try (clientSocket) {
                clientSocket.setSoTimeout(5000); // Guard against slow/hanging clients
                handleConnection(clientSocket);
            } catch (IOException e) {
                System.err.println("Connection error on client " + clientSocket.getRemoteSocketAddress() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Rejection handler when thread pool queue is full: sends HTTP 503 and closes socket.
     */
    private static final class SaturationRejectionHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (r instanceof ConnectionTask task) {
                Socket socket = task.getSocket();
                try {
                    socket.setSoTimeout(3000);
                    Response resp = new Response().status(503, "Service Unavailable")
                            .type("text/plain; charset=utf-8")
                            .header("Connection", "close")
                            .body("503 Service Unavailable: Server is overloaded");
                    resp.writeTo(socket.getOutputStream());
                } catch (IOException ignored) {
                } finally {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }

    private static final class WorkerThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger count = new AtomicInteger(1);

        WorkerThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + count.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }

    private void handleConnection(Socket clientSocket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = clientSocket.getOutputStream();

            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.trim().isEmpty()) {
                return;
            }

            String[] parts = requestLine.trim().split("\\s+");
            if (parts.length < 2) {
                Response badReq = new Response().status(400, "Bad Request")
                        .type("text/plain; charset=utf-8")
                        .body("400 Bad Request: Malformed HTTP request line");
                badReq.writeTo(out);
                return;
            }

            String method = parts[0].toUpperCase();
            String fullUri = parts[1];

            // Read request headers
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                int colonIdx = headerLine.indexOf(':');
                if (colonIdx > 0) {
                    String hName = headerLine.substring(0, colonIdx).trim().toLowerCase();
                    String hVal = headerLine.substring(colonIdx + 1).trim();
                    headers.put(hName, hVal);
                }
            }

            Request req = new Request(method, fullUri, headers);
            System.out.printf("[%s] %s (thread: %s)%n", req.getMethod(), req.getPath(), Thread.currentThread().getName());

            // 1. Method check: Support only GET
            if (!"GET".equalsIgnoreCase(method)) {
                Response methodNotAllowed = new Response().status(405, "Method Not Allowed")
                        .type("text/plain; charset=utf-8")
                        .header("Allow", "GET")
                        .body("405 Method Not Allowed");
                methodNotAllowed.writeTo(out);
                return;
            }

            // 2. Dynamic route lookup
            RouteHandler handler = router.getHandler(method, req.getPath());
            if (handler != null) {
                Response resp = new Response();
                try {
                    String result = handler.handle(req, resp);
                    if (result != null && (resp.getBody() == null || resp.getBody().length == 0)) {
                        resp.body(result);
                    }
                    resp.writeTo(out);
                } catch (Exception e) {
                    System.err.println("Handler exception on path " + req.getPath() + ": " + e.getMessage());
                    Response errResp = new Response().status(500, "Internal Server Error")
                            .type("text/plain; charset=utf-8")
                            .body("500 Internal Server Error: " + (e.getMessage() != null ? e.getMessage() : "Handler error"));
                    errResp.writeTo(out);
                }
                return;
            }

            // 3. Static file lookup
            try {
                StaticFileService.StaticResource staticFile = staticFileService.resolve(req.getPath());
                if (staticFile != null) {
                    Response resp = new Response().status(200, "OK")
                            .type(staticFile.getContentType())
                            .body(staticFile.getBytes());
                    resp.writeTo(out);
                    return;
                }
            } catch (SecurityException se) {
                Response forbidden = new Response().status(400, "Bad Request")
                        .type("text/plain; charset=utf-8")
                        .body("400 Bad Request: " + se.getMessage());
                forbidden.writeTo(out);
                return;
            }

            // 4. Not found -> 404
            Response notFound = new Response().status(404, "Not Found")
                    .type("text/plain; charset=utf-8")
                    .body("404 Not Found");
            notFound.writeTo(out);

        } catch (IOException e) {
            System.err.println("I/O error during request handling: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getBoundPort() {
        return boundPort;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public int getShutdownTimeoutSeconds() {
        return shutdownTimeoutSeconds;
    }
}
