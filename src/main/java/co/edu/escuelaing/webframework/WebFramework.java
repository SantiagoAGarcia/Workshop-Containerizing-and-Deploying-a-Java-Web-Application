package co.edu.escuelaing.webframework;

/**
 * Static façade API for the Mini Web Framework.
 * Provides DSL methods for registering static assets, dynamic GET routes,
 * resolving environment variables, starting, and stopping the HTTP application server.
 */
public class WebFramework {

    private static final Router router = new Router();
    private static final StaticFileService staticFileService = new StaticFileService();
    private static HttpServer server = new HttpServer(router, staticFileService);

    private WebFramework() {
        // Static utility class
    }

    /**
     * Configures the static file resource folder (default "/webroot").
     *
     * @param folder classpath folder path
     */
    public static void staticfiles(String folder) {
        staticFileService.setStaticFolder(folder);
    }

    /**
     * Registers a GET route handler for the specified path.
     *
     * @param path    the route path (e.g. "/hello")
     * @param handler the functional route handler lambda
     */
    public static void get(String path, RouteHandler handler) {
        router.addRoute("GET", path, handler);
    }

    /**
     * Starts the concurrent HTTP server using the port configured in PORT (default: 8080).
     */
    public static void start() {
        start(resolvePort());
    }

    /**
     * Starts the concurrent HTTP server on the given port.
     *
     * @param port the TCP port to listen on
     */
    public static void start(int port) {
        int poolSize = resolveThreadPoolSize();
        int shutdownTimeout = resolveShutdownTimeoutSeconds();
        start(port, poolSize, shutdownTimeout);
    }

    /**
     * Starts the concurrent HTTP server on the given port, pool size, and shutdown timeout.
     *
     * @param port                   the TCP port to listen on
     * @param threadPoolSize         number of worker threads
     * @param shutdownTimeoutSeconds seconds to wait for connection draining
     */
    public static void start(int port, int threadPoolSize, int shutdownTimeoutSeconds) {
        server = new HttpServer(router, staticFileService, threadPoolSize, shutdownTimeoutSeconds);
        server.start(port);
    }

    /**
     * Marks the running server loop as stopped and initiates draining.
     */
    public static void stop() {
        if (server != null) {
            server.stop();
        }
    }

    /**
     * Forces immediate stop (used in unit test teardowns).
     */
    public static void forceStop() {
        if (server != null) {
            server.forceStop();
        }
    }

    /**
     * Clears registered routes and resets framework state (useful for tests).
     */
    public static void reset() {
        if (server != null) {
            server.forceStop();
        }
        router.clear();
        staticFileService.setStaticFolder("/webroot");
    }

    public static HttpServer getServer() {
        return server;
    }

    public static Router getRouter() {
        return router;
    }

    public static StaticFileService getStaticFileService() {
        return staticFileService;
    }

    /**
     * Resolves the server port from the PORT environment variable.
     *
     * @return the resolved integer port (default 8080)
     */
    public static int resolvePort() {
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                int p = Integer.parseInt(envPort.trim());
                if (p > 0 && p <= 65535) {
                    return p;
                }
                System.err.printf("Notice: PORT environment variable '%s' is out of range (1-65535). Falling back to default port 8080.%n", envPort);
            } catch (NumberFormatException e) {
                System.err.printf("Notice: PORT environment variable '%s' is not a valid integer. Falling back to default port 8080.%n", envPort);
            }
        }
        return 8080;
    }

    /**
     * Resolves the thread pool size from THREAD_POOL_SIZE environment variable.
     *
     * @return integer thread pool size (default availableProcessors * 2)
     */
    public static int resolveThreadPoolSize() {
        int defaultSize = Math.max(2, Runtime.getRuntime().availableProcessors() * 2);
        String env = System.getenv("THREAD_POOL_SIZE");
        if (env != null && !env.trim().isEmpty()) {
            try {
                int size = Integer.parseInt(env.trim());
                if (size > 0 && size <= 1000) {
                    return size;
                }
                System.err.printf("Notice: THREAD_POOL_SIZE '%s' is out of range (1-1000). Falling back to default %d.%n", env, defaultSize);
            } catch (NumberFormatException e) {
                System.err.printf("Notice: THREAD_POOL_SIZE '%s' is invalid. Falling back to default %d.%n", env, defaultSize);
            }
        }
        return defaultSize;
    }

    /**
     * Resolves the shutdown timeout from SHUTDOWN_TIMEOUT_SECONDS environment variable.
     *
     * @return integer shutdown timeout in seconds (default 10)
     */
    public static int resolveShutdownTimeoutSeconds() {
        int defaultTimeout = 10;
        String env = System.getenv("SHUTDOWN_TIMEOUT_SECONDS");
        if (env != null && !env.trim().isEmpty()) {
            try {
                int timeout = Integer.parseInt(env.trim());
                if (timeout > 0 && timeout <= 300) {
                    return timeout;
                }
                System.err.printf("Notice: SHUTDOWN_TIMEOUT_SECONDS '%s' is out of range (1-300). Falling back to default %d.%n", env, defaultTimeout);
            } catch (NumberFormatException e) {
                System.err.printf("Notice: SHUTDOWN_TIMEOUT_SECONDS '%s' is invalid. Falling back to default %d.%n", env, defaultTimeout);
            }
        }
        return defaultTimeout;
    }
}
