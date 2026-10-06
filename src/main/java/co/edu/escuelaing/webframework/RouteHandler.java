package co.edu.escuelaing.webframework;

/**
 * Functional interface for handling HTTP requests and producing responses.
 */
@FunctionalInterface
public interface RouteHandler {
    /**
     * Handles an incoming HTTP request and returns the response body.
     *
     * @param req  the parsed incoming HTTP request
     * @param resp the mutable HTTP response object
     * @return the string response body
     * @throws Exception if an error occurs during route handling
     */
    String handle(Request req, Response resp) throws Exception;
}
