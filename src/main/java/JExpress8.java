import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * An express.js-like application library, requires Java 17+
 * <pre>
 *   Run the application with     : java JExpress.java
 * </pre>
 */
public final class JExpress8 {
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
     * The value of an HTTP header or null.
     * @param header the header name.
     * @return value of an HTTP header or null.
     */
    String get(String header);

    /**
     * Get named route parameter
     * @param name name of the parameter
     * @return the value of the parameter or ""
     */
    String param(String name);

    /**
     * Returns the body of the request as a JSON array.
     * @return the body of the request as a JSON array.
     * @throws IOException if an I/O error occurs.
     */
    List<Object> bodyArray() throws IOException;

    /**
     * Returns the body of the request as a JSON object.
     * @return the body of the request as a JSON object.
     * @throws IOException if an I/O error occurs.
     */
    Map<String, Object> bodyObject() throws IOException;

    /**
     * Returns the body of the request as an InputStream.
     * @return the body of the request as an InputStream.
     */
    InputStream bodyStream();

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
     * @param object an object, can be an iterable, a stream, a record or a map
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

  private static final class RequestImpl implements Request {
    private final HttpExchange exchange;
    private final String[] components;

    private RequestImpl(HttpExchange exchange, String[] components) {
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
    public String get(String header) {
      return exchange.getRequestHeaders().getFirst(header);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String param(String name) {
      return ((Map<String, String>) exchange.getAttribute("params")).getOrDefault(name, "");
    }

    @Override
    public InputStream bodyStream() {
      return exchange.getRequestBody();
    }

    private Object body() throws IOException {
      if (!"application/json".equals(get("Content-Type"))) {
        throw new IllegalStateException("Content-Type is not 'application/json'");
      }
      return ToyJSONParser.parse(bodyText());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> bodyObject() throws IOException {
      return (Map<String, Object>) body();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object> bodyArray() throws IOException {
      return (List<Object>) body();
    }

    @Override
    public String bodyText() throws IOException {
      try (InputStream in = bodyStream();
           InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
           BufferedReader buffered = new BufferedReader(reader)) {
        return buffered.lines().collect(Collectors.joining("\n"));
      }
    }
  }

  private static final class ResponseImpl implements Response {
    private final HttpExchange exchange;

    private ResponseImpl(HttpExchange exchange) {
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
      json(JSONPrettyPrinter.toJSON(object));
    }
  
    @Override
    public void json(String json) throws IOException {
      type("application/json", "utf-8");
      send(json);
    }
  
    @Override
    public void send(String body) throws IOException {
      byte[] content = body.getBytes(StandardCharsets.UTF_8);
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
        Headers headers = exchange.getResponseHeaders();
        String contentType = headers.getFirst("Content-Type");
        if (contentType == null) {
          contentType = Files.probeContentType(path);
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

        long contentLength = Files.size(path);
        exchange.sendResponseHeaders(200, contentLength);
        System.err.println("  send file " + 200 + " content-type " + contentType + " content-length " + contentLength);
  
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

  // A Toy JSON parser that do not recognize correctly, unicode characters, escaped strings,
  // and I'm sure many more features.
  /*private*/ static class ToyJSONParser {
    private ToyJSONParser() {
      throw new AssertionError();
    }

    enum Kind {
      NULL("(null)"),
      TRUE("(true)"),
      FALSE("(false)"),
      DOUBLE("([0-9]*\\.[0-9]*)"),
      INTEGER("([0-9]+)"),
      STRING("\"([^\\\"]*)\""),
      LEFT_CURLY("(\\{)"),
      RIGHT_CURLY("(\\})"),
      LEFT_BRACKET("(\\[)"),
      RIGHT_BRACKET("(\\])"),
      COLON("(\\:)"),
      COMMA("(\\,)"),
      BLANK("([ \t]+)")
      ;

      private final String regex;

      Kind(String regex) {
        this.regex = regex;
      }

      private static final Kind[] VALUES = values();
    }

    private static final class Token {
      private final Kind kind;
      private final String text;
      private final int location;

      private Token(Kind kind, String text, int location) {
        this.kind = kind;
        this.text = text;
        this.location = location;
      }

      private boolean is(Kind kind) {
       return this.kind == kind;
     }

     private String expect(Kind kind) {
       if (this.kind != kind) {
         throw error(kind);
       }
       return text;
     }

     public IllegalStateException error(Kind... expectedKinds) {
       return new IllegalStateException("expect " + Arrays.stream(expectedKinds)
           .map(Kind::name).collect(Collectors.joining(", ")) + " but recognized " + kind + " at " + location);
     }

      @Override
      public String toString() {
        return "Token[" +
            "kind=" + kind + ", " +
            "text=" + text + ", " +
            "location=" + location + ']';
      }

        }

    private static final class Lexer {
      private final Matcher matcher;

      private Lexer(Matcher matcher) {
        this.matcher = matcher;
      }

      private Token next() {
        for (; ; ) {
          if (!matcher.find()) {
            throw new IllegalStateException("no token recognized");
          }
          int index = IntStream.rangeClosed(1, matcher.groupCount()).filter(i -> matcher.group(i) != null).findFirst().getAsInt();
          Kind kind = Kind.VALUES[index - 1];
          if (kind != Kind.BLANK) {
            return new Token(kind, matcher.group(index), matcher.start(index));
          }
        }
      }
    }

    private static final Pattern PATTERN = Pattern.compile(Arrays.stream(Kind.VALUES)
        .map(k -> k.regex).collect(Collectors.joining("|")));

    /**
     * Parse a JSON text.
     *
     * @param input a JSON text
     * @return a Java object corresponding to the text
     */
    public static Object parse(String input) {
      Lexer lexer = new Lexer(PATTERN.matcher(input));
      try {
        return parse(lexer);
      } catch(IllegalStateException e) {
        throw new IllegalStateException(e.getMessage() + "\n while parsing " + input, e);
      }
    }

    private static Object parse(Lexer lexer) {
      Token token = lexer.next();
      switch (token.kind) {
        case LEFT_CURLY:
          HashMap<String, Object> object = new HashMap<String, Object>();
          parseObject(lexer, object);
          return object;
        case LEFT_BRACKET:
          ArrayList<Object> array = new ArrayList<>();
          parseArray(lexer, array);
          return array;
        default:
          throw token.error(Kind.LEFT_CURLY, Kind.LEFT_BRACKET);
      }
    }

    private static void parseObjectValue(Token token, Lexer lexer, Map<String, Object> jsonObject, String key) {
      switch (token.kind) {
        case NULL:
          jsonObject.put(key, null);
          break;
        case FALSE:
          jsonObject.put(key, false);
          break;
        case TRUE:
          jsonObject.put(key, true);
          break;
        case INTEGER:
          jsonObject.put(key, Integer.parseInt(token.text));
          break;
        case DOUBLE:
          jsonObject.put(key, Double.parseDouble(token.text));
          break;
        case STRING:
          jsonObject.put(key, token.text);
          break;
        case LEFT_CURLY:
          HashMap<String, Object> map = new HashMap<String, Object>();
          parseObject(lexer, map);
          jsonObject.put(key, map);
          break;
        case LEFT_BRACKET:
          ArrayList<Object> list = new ArrayList<Object>();
          parseArray(lexer, list);
          jsonObject.put(key, list);
          break;
        default:
          throw token.error(Kind.NULL, Kind.FALSE, Kind.TRUE, Kind.INTEGER, Kind.DOUBLE, Kind.STRING, Kind.LEFT_BRACKET, Kind.RIGHT_CURLY);
      }
    }

    private static void parseArrayValue(Token token, Lexer lexer, List<Object> jsonArray) {
      switch (token.kind) {
        case NULL:
          jsonArray.add(null);
          break;
        case FALSE:
          jsonArray.add(false);
          break;
        case TRUE:
          jsonArray.add(true);
          break;
        case INTEGER:
          jsonArray.add(Integer.parseInt(token.text));
          break;
        case DOUBLE:
          jsonArray.add(Double.parseDouble(token.text));
          break;
        case STRING:
          jsonArray.add(token.text);
          break;
        case LEFT_CURLY:
          HashMap<String, Object> map = new HashMap<String, Object>();
          parseObject(lexer, map);
          jsonArray.add(map);
          break;
        case LEFT_BRACKET:
          ArrayList<Object> list = new ArrayList<>();
          parseArray(lexer, list);
          jsonArray.add(list);
          break;
        default:
          throw token.error(Kind.NULL, Kind.FALSE, Kind.TRUE, Kind.INTEGER, Kind.DOUBLE, Kind.STRING, Kind.LEFT_BRACKET, Kind.RIGHT_CURLY);
      }
    }

    private static void parseObject(Lexer lexer, Map<String, Object> jsonObject) {
      Token token = lexer.next();
      if (token.is(Kind.RIGHT_CURLY)) {
        return;
      }
      for(;;) {
        String key = token.expect(Kind.STRING);
        lexer.next().expect(Kind.COLON);
        token = lexer.next();
        parseObjectValue(token, lexer, jsonObject, key);
        token = lexer.next();
        if (token.is(Kind.RIGHT_CURLY)) {
          return;
        }
        token.expect(Kind.COMMA);
        token = lexer.next();
      }
    }

    private static void parseArray(Lexer lexer, List<Object> jsonArray) {
      Token token = lexer.next();
      if (token.is(Kind.RIGHT_BRACKET)) {
        return;
      }
      for(;;) {
        parseArrayValue(token, lexer, jsonArray);
        token = lexer.next();
        if (token.is(Kind.RIGHT_BRACKET)) {
          return;
        }
        token.expect(Kind.COMMA);
        token = lexer.next();
      }
    }
  }

  private static final class JSONPrettyPrinter {
    private static String toJSON(Object o) {
      if (o instanceof Collection<?>) {
        Collection<?> collection = (Collection<?>) o;
        return toJSONArray(collection.stream());
      }
      if (o instanceof Iterable<?>) {
        Iterable<?> iterable = (Iterable<?>) o;
        return toJSONArray(StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterable.iterator(), 0), false));
      }
      if (o instanceof Stream<?>) {
        Stream<?> stream = (Stream<?>) o;
        return toJSONArray(stream);
      }
      if (o instanceof Map<?, ?>) {
        Map<?, ?> map = (Map<?, ?>) o;
        return toJSONObject(map);
      }
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
      return stream.map(JSONPrettyPrinter::toJSONItem).collect(Collectors.joining(", ", "[", "]"));
    }
    private static String toJSONObject(Map<?,?> map) {
      return map.entrySet().stream()
          .map(e -> "\"" + e.getKey() + "\": " + toJSONItem(e.getValue()))
          .collect(Collectors.joining(", ", "{", "}"));
    }
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
    Pipeline oldPipeline = pipeline;
    Function<String[], Optional<Map<String, String>>> matcher = matcher(path);
    pipeline = (request, response) -> {
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
   * This method is usually used in conjunction of {@link #use(String, Handler)}.
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
   * @throws UncheckedIOException if an I/O error occurs when creating the server.
   */
  public JExpress.Server listen(int port) {
    HttpServer server;
    try {
      server = HttpServer.create(new InetSocketAddress(port), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
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
   * Run a simple web server that serve static files from the current directory.
   * @param args no argument
   */
  public static void main(String[] args) {
    JExpress8 app = express();
    app.use(staticFiles(Paths.get(".")));
    app.listen(8080);

    System.out.println("application started on port 8080");
  }
}
