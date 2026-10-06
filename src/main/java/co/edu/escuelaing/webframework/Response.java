package co.edu.escuelaing.webframework;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encapsulates the HTTP response, allowing status code, content-type,
 * custom headers, and response body to be configured.
 */
public class Response {

    private int statusCode = 200;
    private String statusMessage = "OK";
    private String contentType = "text/plain; charset=utf-8";
    private final Map<String, String> headers = new LinkedHashMap<>();
    private byte[] body = new byte[0];

    public Response() {
    }

    public Response status(int code) {
        this.statusCode = code;
        this.statusMessage = defaultStatusMessage(code);
        return this;
    }

    public Response status(int code, String message) {
        this.statusCode = code;
        this.statusMessage = message;
        return this;
    }

    public Response type(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public Response header(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    public Response body(String text) {
        if (text != null) {
            this.body = text.getBytes(StandardCharsets.UTF_8);
        } else {
            this.body = new byte[0];
        }
        return this;
    }

    public Response body(byte[] bytes) {
        this.body = bytes != null ? bytes : new byte[0];
        return this;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public String getContentType() {
        return contentType;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }

    /**
     * Serializes this response to the provided output stream.
     * Always appends Content-Length and Connection: close.
     *
     * @param out the socket output stream
     * @throws IOException if a write error occurs
     */
    public void writeTo(OutputStream out) throws IOException {
        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusMessage).append("\r\n");

        if (contentType != null && !contentType.isEmpty()) {
            headerBuilder.append("Content-Type: ").append(contentType).append("\r\n");
        }

        headerBuilder.append("Content-Length: ").append(body.length).append("\r\n");
        headerBuilder.append("Connection: close\r\n");

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (!"content-type".equalsIgnoreCase(entry.getKey())
                    && !"content-length".equalsIgnoreCase(entry.getKey())
                    && !"connection".equalsIgnoreCase(entry.getKey())) {
                headerBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            }
        }

        headerBuilder.append("\r\n");

        out.write(headerBuilder.toString().getBytes(StandardCharsets.UTF_8));
        if (body.length > 0) {
            out.write(body);
        }
        out.flush();
    }

    public static String defaultStatusMessage(int code) {
        return switch (code) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            case 503 -> "Service Unavailable";
            default -> "Status " + code;
        };
    }
}
