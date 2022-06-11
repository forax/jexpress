import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.System.out;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.joining;

/**
 * An express.js-like application library, requires Java 8
 * <pre>
 *   Compile the application with : javac JExpress8.java
 *   Run the application with     : java JExpress8
 * </pre>
 */
@SuppressWarnings("restriction")
public class JExpress8 {
  /**
   * A HTTP request
   */
  public interface Request {
    /**
     * The HTTP method of the request.
     * @return the HTTP method.
     */
    String method();

    /**
     * The HTTP path of the request.
     * @return the HTTP path.
     */
    String path();

    /**
     * Get named route parameter
     * @param name name of the parameter
     * @return the value of the parameter or ""
     */
    String param(String name);

    /**
     * Returns the body of the request
     * @return the body of the request
     */
    InputStream body();

    /**
     * Returns the body of the request as a String.
     * @return the body of the request as a String.
     * @throws IOException if an I/O error occurs.
     */
    String bodyText() throws IOException;
  }

  /**
   * A HTTP response
   */
  public interface Response {
    /**
     * Set the HTTP status of the response
     * @param status the HTTP status (200, 404, etc)
     * @return the current response.
     */
    Response status(int status);

    /**
     * Appends the specified value to the HTTP response header field.
     * @param field a header field name
     * @param value the value of the header field
     * @return the current response.
     */
    Response append(String field, String value);

    /**
     * Sets the response’s HTTP header field to value
     * @param field a header field name
     * @param value the value of the header field
     * @return the current response.
     */
    Response set(String field, String value);

    /**
     * Sets the Content-Type HTTP header to the MIME type.
     * @param type the MIME type.
     * @return the current response.
     */
    Response type(String type);

    /**
     * Sets the Content-Type HTTP header and the charset encoding.
     * @param type the MIME type.
     * @param charset the charset encoding
     * @return the current response.
     */
    default Response type(String type, String charset) {
      return type(type + "; charset=" + charset);
    }

    /**
     * Sets the Content-Type HTTP header and the charset encoding.
     * @param type the MIME type.
     * @param charset the charset encoding
     * @return the current response.
     */
    default Response type(String type, Charset charset) {
      return type(type, charset.name());
    }

    /**
     * Sends a JSON response with the correct 'Content-Type'.
     * @param object an object, can be an iterable, a stream or a map
     * @throws IOException if an I/O error occurs.
     */
    void json(Object object) throws IOException;

    /**
     * Sends a JSON response with the correct 'Content-Type'.
     * @param json a JSON string
     * @throws IOException if an I/O error occurs.
     */
    void json(String json) throws IOException;

    /**
     * Send a Text response.
     * If the status is not defined 200 will be used.
     * If the Content-Type is not defined, "text/html" will be used.
     * @param body the text of the response
     * @throws IOException if an I/O error occurs.
     */
    void send(String body) throws IOException;

    /**
     * Send a file as response.
     * The status is set to 200 if the file is found or 404 if not found.
     * @param path the path of the file;
     * @throws IOException if an I/O error occurs.
     */
    void sendFile(Path path) throws IOException;
  }


  /**
   * A generic handler called to process an HTTP request in order to
   * create an HTTP response.
   *
   * @see #use(Handler)
   * @see Callback
   */
  @FunctionalInterface
  public interface Handler {
    /**
     * Represent the next handler in the handler chain.
     */
    @FunctionalInterface
    interface Chain {
      /**
       * Calls the next handler in the handler chain.
       *
       * @throws IOException if an I/O occurs
       */
      void next() throws IOException;
    }

    /**
     * Called to process a HTTP request in order to create a HTTP response.
     * @param request a HTTP request
     * @param response a HTTP response
     * @param chain represents the next handler in the handler chain
     * @throws IOException if an I/O occurs
     */
    void handle(Request request, Response response, Chain chain) throws IOException;
  }

  /**
   * A callback called to process an HTTP request in order to
   * create an HTTP response.
   * {@link Handler} is a more generic interface which unlike {@link Callback} allows to
   * delegate part of the processing to another handler.
   */
  @FunctionalInterface
  public interface Callback {
    /**
     * Called to process a HTTP request in order to create a HTTP response.
     * @param request a HTTP request
     * @param response a HTTP response
     * @throws IOException if an I/O occurs
     */
    void accept(Request request, Response response) throws IOException;
  }

  /**
   * A server instance
   */
  public interface Server extends AutoCloseable {
    /**
     * Close the server
     */
    void close();
  }

  private static final class  RequestImpl implements Request {
    private final HttpExchange exchange;
    private final String[] components;

    RequestImpl(HttpExchange exchange, String[] components) {
      this.exchange = exchange;
      this.components = components;
    }

    @Override
    public String method() {
      return exchange.getRequestMethod().toUpperCase(Locale.ROOT);
    }

    @Override
    public String path() {
      return exchange.getRequestURI().getPath();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String param(String name) {
      return ((Map<String, String>) exchange.getAttribute("params")).getOrDefault(name, "");
    }

    @Override
    public InputStream body() {
      return exchange.getRequestBody();
    }

    @Override
    public String bodyText() throws IOException {
      try (InputStream in = exchange.getRequestBody();
           InputStreamReader reader = new InputStreamReader(in, UTF_8);
           BufferedReader buffered = new BufferedReader(reader)) {
        return buffered.lines().collect(joining("\n"));
      }
    }
  }

  private static final class ResponseImpl implements Response {
    private final HttpExchange exchange;

    ResponseImpl(HttpExchange exchange) {
      this.exchange = exchange;
    }

    @Override
    public Response status(int status) {
      exchange.setAttribute("status", status);
      return this;
    }

    @Override
    public Response append(String field, String value) {
      exchange.getResponseHeaders().add(field, value);
      return this;
    }

    @Override
    public Response set(String field, String value) {
      exchange.getResponseHeaders().set(field, value);
      return this;
    }

    @Override
    public Response type(String type) {
      return set("Content-Type", type);
    }

    @Override
    public void json(Object object) throws IOException {
      json(toJSON(object));
    }

    @Override
    public void json(String json) throws IOException {
      type("application/json", "utf-8");
      send(json);
    }

    @Override
    public void send(String body) throws IOException {
      byte[] content = body.getBytes(UTF_8);
      int status = (int) exchange.getAttribute("status");
      int contentLength = content.length;
      exchange.sendResponseHeaders(status, contentLength);
      System.err.println("  send " + status + " content-length " + contentLength);

      Headers headers = exchange.getResponseHeaders();
      if (!headers.containsKey("Content-Type")) {
        type("text/html", "utf-8");
      }
      try (OutputStream output = exchange.getResponseBody()) {
        output.write(content);
        output.flush();
      }
    }

    @Override
    public void sendFile(Path path) throws IOException {
      try (InputStream input = Files.newInputStream(path)) {
        long contentLength = Files.size(path);
        exchange.sendResponseHeaders(200, contentLength);
        System.err.println("  send file " + 200 + " content-length " + contentLength);

        Headers headers = exchange.getResponseHeaders();
        if (!headers.containsKey("Content-Type")) {
          String contentType = Files.probeContentType(path);
          if (contentType == null) {
            contentType = "application/octet-stream";
          }
          //System.err.println("inferred content type " + contentType);
          if (contentType.startsWith("text/")) {
            type(contentType, "utf-8");
          } else {
            type(contentType);
          }
        }

        try (OutputStream output = exchange.getResponseBody()) {
          byte[] buffer = new byte[8192];
          int read;
          while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
          }
          output.flush();
        }
      } catch (FileNotFoundException e) {
        String message = "Not Found " + e.getMessage();
        //System.err.println(message);
        status(404).send("<html><h2>" + message + "</h2></html>");
      }
    }
  }

  private static String toJSON(Object o) {
    if (o instanceof Collection<?>) {
      Collection<?> collection = (Collection<?>) o;
      return toJSONArray(collection.stream());
    }
    if (o instanceof Iterable<?>) {
      Iterable<?> iterable = (Iterable<?>) o;
      return toJSONArray(StreamSupport.stream(spliteratorUnknownSize(iterable.iterator(), 0), false));
    }
    if (o instanceof Stream<?>) {
      Stream<?> stream = (Stream<?>) o;
      return toJSONArray(stream);
    }
    if (o instanceof Map<?,?>) {
      Map<?,?> map = (Map<?, ?>) o;
      return toJSONObject(map);
    }
    //if (o instanceof Record record) {
    //  return toJSONObject(record);
    //}
    throw new IllegalStateException("unknown json object " + o);
  }
  private static String toJSONItem(Object item) {
    if (item == null) {
      return "null";
    }
    if (item instanceof String) {
      return "\"" + item + '"';
    }
    if (item instanceof Boolean || item instanceof Integer || item instanceof Double) {
      return item.toString();
    }
    return toJSON(item);
  }
  private static String toJSONArray(Stream<?> stream) {
    return stream.map(JExpress8::toJSONItem).collect(joining(", ", "[", "]"));
  }
  private static String toJSONObject(Map<?,?> map) {
    return map.entrySet().stream()
        .map(e -> "\"" + e.getKey() + "\": " + toJSONItem(e.getValue()))
        .collect(joining(", ", "{", "}"));
  }
  //private static Object accessor(Method accessor, Record record) {
  //  try {
  //    return accessor.invoke(record);
  //  } catch (IllegalAccessException e) {
  //    throw (IllegalAccessError) new IllegalAccessError().initCause(e);
  //  } catch (InvocationTargetException e) {
  //    Throwable cause = e.getCause();
  //    if (cause instanceof RuntimeException runtimeException) {
  //      throw runtimeException;
  //    }
  //    if (cause instanceof Error error) {
  //      throw error;
  //    }
  //    throw new UndeclaredThrowableException(cause);
  //  }
  //}
  //private static String toJSONObject(Record record) {
  //  return Arrays.stream(record.getClass().getRecordComponents())
  //      .map(c -> "\"" + c.getName() + "\": " + toJSONItem(accessor(c.getAccessor(), record)))
  //      .collect(joining(", ", "{", "}"));
  //}

  private static Function<String[], Optional<Map<String, String>>> matcher(String uri) {
    String[] parts = uri.split("/");
    int length = parts.length;
    Predicate<String[]> predicate =  (Predicate<String[]>) components -> components.length >= length;
    BiConsumer<String[], Map<String, String>> consumer = (BiConsumer<String[], Map<String,String>>) (_1, _2) -> { /* empty */ };
    for(int i = 0; i < length; i++) {
      int index = i;
      String part = parts[i];
      if (part.startsWith(":")) {
        String key = part.substring(1);
        BiConsumer<String[], Map<String, String>> c = consumer;
        consumer = (components, map) -> { c.accept(components, map); map.put(key, components[index]); };
      } else {
        predicate = predicate.and(components -> part.equals(components[index]));
      }
    }

    Predicate<String[]> p =  predicate;
    BiConsumer<String[], Map<String, String>> c = consumer;
    return components -> {
      if (!p.test(components)) {
        //System.err.println("do not match " + Arrays.toString(components));
        return Optional.empty();
      }
      HashMap<String, String> map = new HashMap<String,String>();
      c.accept(components, map);
      //System.err.println("match " + Arrays.toString(components) + " " + map);
      return Optional.of(map);
    };
  }

  private JExpress8() {
    // empty
  }

  /**
   * Creates an Express like application.
   * @return a new Express like application.
   */
  public static JExpress8 express() {
    return new JExpress8();
  }

  @FunctionalInterface
  private interface Pipeline {
    void accept(RequestImpl request, ResponseImpl response) throws IOException;
  }

  private Pipeline pipeline = (request, response) -> {
    String message = "no match " + request.method() + " " + request.path();
    response.status(404).send("<html><h2>" + message + "</h2></html>");
  };

  /**
   * Routes an HTTP request if the HTTP method is GET.
   * @param path a string representation of a path with interpolation.
   * @param callback a callback called when a client emits a
   *        HTTP request to create an HTTP response.
   */
  public void get(String path, Callback callback) {
    method("GET", path, callback);
  }

  /**
   * Routes an HTTP request if the HTTP method is POST.
   * @param path a string representation of a path with interpolation.
   * @param callback a callback called when a client emits a
   *        HTTP request to create an HTTP response.
   */
  public void post(String path, Callback callback) {
    method("POST", path, callback);
  }

  /**
   * Routes an HTTP request if the HTTP method is PUT.
   * @param path a string representation of a path with interpolation.
   * @param callback a callback called when a client emits a
   *        HTTP request to create an HTTP response.
   */
  public void put(String path, Callback callback) {
    method("PUT", path, callback);
  }

  /**
   * Routes an HTTP request if the HTTP method is DELETE.
   * @param path a string representation of a path with interpolation.
   * @param callback a callback called when a client emits a
   *        HTTP request to create an HTTP response.
   */
  public void delete(String path, Callback callback) {
    method("DELETE", path, callback);
  }

  private void method(String method, String path, Callback callback) {
    use(path, (request, response, chain) -> {
      if (request.method().equalsIgnoreCase(method)) {
        callback.accept(request, response);
      } else {
        chain.next();
      }
    });
  }

  /**
   * Register a handler that is always match the requested path.
   * This method is semantically equivalent to
   * <pre>
   *   use("/", handler);
   * </pre>
   *
   * @param handler the handler called to call
   * @see #use(String, Handler)
   */
  public void use(Handler handler) {
    use("/", handler);
  }

  /**
   * Register a handler that is called when the request path match the defined path
   * @param path the defined path that the request path must match
   * @param handler the handler called if the requested path match
   */
  public void use(String path, Handler handler) {
    Pipeline oldPipeline = this.pipeline;
    Function<String[], Optional<Map<String, String>>> matcher = matcher(path);
    this.pipeline = (request, response) -> {
      Optional<Map<String, String>> paramsOpt = matcher.apply(request.components);
      if (paramsOpt.isPresent()) {
        request.exchange.setAttribute("params", paramsOpt.get());
        handler.handle(request, response, () -> oldPipeline.accept(request, response));
      } else {
        oldPipeline.accept(request, response);
      }
    };
  }

  /**
   * Serve static files from a root directory.
   * This method is usually used in conjuction of {@link #use(String, Handler)}.
   * For example,
   * <pre>
   *   app.use(staticFiles(Path.of("."));
   * </pre>
   * @param root the root directory
   * @return a handler that serves static files from the root directory
   */
  public static Handler staticFiles(Path root) {
    return (request, response, chain) -> {
      Path path = Paths.get(root.toString(), request.path());
      response.sendFile(path);
    };
  }

  /**
   * Starts a server on the given port and listen for connections.
   * @param port a TCP port
   * @return the server instance
   * @throws IOException if an I/O error occurs.
   */
  public Server listen(int port) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/", exchange -> {
      System.err.println("request " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
      try {
        String[] components = exchange.getRequestURI().getPath().split("/");
        exchange.setAttribute("status", 200);
        pipeline.accept(new RequestImpl(exchange, components), new ResponseImpl(exchange));
        //exchange.close();
      } catch(Exception e) {
        e.printStackTrace();
        throw e;
      }
    });
    server.setExecutor(null);
    server.start();
    return () -> server.stop(1);
  }



  // ---------------------------------------------------------- //
  //  DO NOT EDIT ABOVE THIS LINE                               //
  // ---------------------------------------------------------- //

  /**
   * Run a simple web server that serve static files of the current directory.
   * @param args no argument
   * @throws IOException if an IO error occurs
   */
  public static void main(String[] args) throws IOException {
    JExpress8 app = express();
    app.use(staticFiles(Paths.get(".")));
    app.listen(8080);

    out.println("application started on port 8080");
  }
}
