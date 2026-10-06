package co.edu.escuelaing.webframework;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates an incoming HTTP request, exposing method, path,
 * raw query string, and decoded query parameters.
 */
public class Request {

    private final String method;
    private final String path;
    private final String rawQuery;
    private final Map<String, List<String>> queryParams;
    private final Map<String, String> headers;

    public Request(String method, String fullUri, Map<String, String> headers) {
        this.method = method != null ? method.trim().toUpperCase() : "GET";
        this.headers = headers != null ? Collections.unmodifiableMap(new LinkedHashMap<>(headers)) : Collections.emptyMap();

        String parsedPath;
        String parsedQuery = null;

        if (fullUri == null || fullUri.isEmpty()) {
            parsedPath = "/";
        } else {
            int qIdx = fullUri.indexOf('?');
            if (qIdx >= 0) {
                parsedPath = fullUri.substring(0, qIdx);
                parsedQuery = fullUri.substring(qIdx + 1);
            } else {
                parsedPath = fullUri;
            }
        }

        this.path = normalizePath(parsedPath);
        this.rawQuery = parsedQuery;
        this.queryParams = parseQueryParams(parsedQuery);
    }

    private static String normalizePath(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "/";
        }
        return raw.startsWith("/") ? raw : "/" + raw;
    }

    private static Map<String, List<String>> parseQueryParams(String rawQuery) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return Collections.unmodifiableMap(map);
        }

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int eqIdx = pair.indexOf('=');
            String rawKey;
            String rawVal;
            if (eqIdx >= 0) {
                rawKey = pair.substring(0, eqIdx);
                rawVal = pair.substring(eqIdx + 1);
            } else {
                rawKey = pair;
                rawVal = "";
            }

            try {
                String decodedKey = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
                String decodedVal = URLDecoder.decode(rawVal, StandardCharsets.UTF_8);
                map.computeIfAbsent(decodedKey, k -> new ArrayList<>()).add(decodedVal);
            } catch (Exception ignored) {
                // Ignore any malformed fragments without breaking the server
            }
        }

        return Collections.unmodifiableMap(map);
    }

    /**
     * Returns the first decoded query parameter value associated with the specified key,
     * or null if the key does not exist.
     *
     * @param key the parameter name
     * @return the decoded string value or null
     */
    public String getValue(String key) {
        if (key == null) {
            return null;
        }
        List<String> values = queryParams.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    /**
     * Returns all decoded values for the specified key.
     *
     * @param key the parameter name
     * @return list of decoded values or empty list if absent
     */
    public List<String> getValues(String key) {
        if (key == null) {
            return Collections.emptyList();
        }
        List<String> values = queryParams.get(key);
        return values != null ? Collections.unmodifiableList(values) : Collections.emptyList();
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getRawQuery() {
        return rawQuery;
    }

    public Map<String, List<String>> getQueryParams() {
        return queryParams;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHeader(String name) {
        if (name == null) {
            return null;
        }
        return headers.get(name.toLowerCase());
    }
}
