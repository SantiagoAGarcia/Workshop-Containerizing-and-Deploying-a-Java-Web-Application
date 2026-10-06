package co.edu.escuelaing.webframework;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Service responsible for locating and reading static resources (HTML, CSS, JS, images)
 * from either an external filesystem directory or the classpath.
 * Enforces strict path traversal defenses and handles all file content as raw bytes.
 * Fully thread-safe with volatile staticFolder configuration and immutable resource returns.
 */
public class StaticFileService {

    private volatile String staticFolder = "/webroot";
    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put("html", "text/html; charset=utf-8");
        MIME_TYPES.put("htm", "text/html; charset=utf-8");
        MIME_TYPES.put("css", "text/css; charset=utf-8");
        MIME_TYPES.put("js", "application/javascript; charset=utf-8");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("ico", "image/x-icon");
        MIME_TYPES.put("json", "application/json; charset=utf-8");
        MIME_TYPES.put("txt", "text/plain; charset=utf-8");
    }

    public static class StaticResource {
        private final byte[] bytes;
        private final String contentType;

        public StaticResource(byte[] bytes, String contentType) {
            this.bytes = bytes;
            this.contentType = contentType;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public String getContentType() {
            return contentType;
        }
    }

    public void setStaticFolder(String folder) {
        if (folder == null || folder.trim().isEmpty()) {
            this.staticFolder = "/webroot";
        } else {
            String clean = folder.trim();
            this.staticFolder = clean.startsWith("/") ? clean : "/" + clean;
        }
    }

    public String getStaticFolder() {
        return staticFolder;
    }

    /**
     * Checks whether the given requested path constitutes a path traversal attempt.
     *
     * @param path requested path
     * @return true if unsafe, false otherwise
     */
    public boolean isPathTraversal(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase();
        if (lower.contains("..") || lower.contains("\\") || lower.contains("//")
                || lower.contains("%2e") || lower.contains("%2f") || lower.contains("\0")) {
            return true;
        }
        try {
            Path normalized = Paths.get("/root", path).normalize();
            if (!normalized.startsWith(Paths.get("/root"))) {
                return true;
            }
        } catch (Exception e) {
            return true;
        }
        return false;
    }

    /**
     * Attempts to resolve a static file for the given path.
     *
     * @param requestPath requested URI path
     * @return StaticResource if found, or null
     * @throws SecurityException if path traversal is detected
     */
    public StaticResource resolve(String requestPath) {
        if (requestPath == null || requestPath.isEmpty() || requestPath.equals("/")) {
            requestPath = "/index.html";
        }

        if (isPathTraversal(requestPath)) {
            throw new SecurityException("Path traversal attempt detected: " + requestPath);
        }

        String normalizedPath = requestPath.startsWith("/") ? requestPath : "/" + requestPath;

        // 1. Check STATIC_FILES_PATH environment variable if defined
        String externalRoot = System.getenv("STATIC_FILES_PATH");
        if (externalRoot != null && !externalRoot.trim().isEmpty()) {
            File externalFile = new File(externalRoot.trim(), normalizedPath.substring(1));
            if (externalFile.exists() && externalFile.isFile() && externalFile.canRead()) {
                try (InputStream in = new FileInputStream(externalFile)) {
                    byte[] data = in.readAllBytes();
                    return new StaticResource(data, getMimeType(normalizedPath));
                } catch (IOException ignored) {
                }
            }
        }

        // 2. Fallback to Classpath resource loading
        String resourcePath = staticFolder + normalizedPath;
        try (InputStream in = StaticFileService.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                byte[] data = in.readAllBytes();
                return new StaticResource(data, getMimeType(normalizedPath));
            }
        } catch (IOException ignored) {
        }

        // 3. Fallback to local project directory during development
        File devFile = new File("src/main/resources" + staticFolder, normalizedPath.substring(1));
        if (devFile.exists() && devFile.isFile() && devFile.canRead()) {
            try (InputStream in = new FileInputStream(devFile)) {
                byte[] data = in.readAllBytes();
                return new StaticResource(data, getMimeType(normalizedPath));
            } catch (IOException ignored) {
            }
        }

        return null;
    }

    /**
     * Determines Content-Type header based on file extension.
     */
    public String getMimeType(String path) {
        if (path == null) {
            return "application/octet-stream";
        }
        int dotIdx = path.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx < path.length() - 1) {
            String ext = path.substring(dotIdx + 1).toLowerCase();
            String mime = MIME_TYPES.get(ext);
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }
}
