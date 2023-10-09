import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.System.out;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.rangeClosed;

/**
 * An express.js-like application library, requires Java 17+
 * <pre>
 *   Run the application with     : java JExpress.java
 * </pre>
 */
public class JExpress {
  /**
   * A HTTP request
   */
  public sealed interface Request {
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
  public sealed interface Response {
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
  interface Server extends AutoCloseable {
    /**
     * Close the server
     */
    void close();
  }

  private record RequestImpl(HttpExchange exchange, String[] components) implements Request {
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
      try (var in = bodyStream();
           var reader = new InputStreamReader(in, UTF_8);
           var buffered = new BufferedReader(reader)) {
        return buffered.lines().collect(joining("\n"));
      }
    }
  }

  private record ResponseImpl(HttpExchange exchange) implements Response {
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
      var content = body.getBytes(UTF_8);
      var status = (int) exchange.getAttribute("status");
      var contentLength = content.length;
      exchange.sendResponseHeaders(status, contentLength);
      System.err.println("  send " + status + " content-length " + contentLength);

      var headers = exchange.getResponseHeaders();
      if (!headers.containsKey("Content-Type")) {
        type("text/html", "utf-8");
      }
      try (var output = exchange.getResponseBody()) {
        output.write(content);
        output.flush();
      }
    }

    @Override
    public void sendFile(Path path) throws IOException {
      try (var input = Files.newInputStream(path)) {
        var headers = exchange.getResponseHeaders();
        var contentType = headers.getFirst("Content-Type");
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

        var contentLength = Files.size(path);
        exchange.sendResponseHeaders(200, contentLength);
        System.err.println("  send file " + 200 + " content-type " + contentType + " content-length " + contentLength);

        try (var output = exchange.getResponseBody()) {
          var buffer = new byte[8192];
          int read;
          while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
          }
          output.flush();
        }
      } catch (FileNotFoundException e) {
        var message = "Not Found " + e.getMessage();
        //System.err.println(message);
        status(404).send("<html><h2>" + message + "</h2></html>");
      }
    }
  }

  private static Function<String[], Optional<Map<String, String>>> matcher(String uri) {
    var parts = uri.split("/");
    var length = parts.length;
    var predicate =  (Predicate<String[]>) components -> components.length >= length;
    var consumer = (BiConsumer<String[], Map<String,String>>) (_1, _2) -> { /* empty */ };
    for(var i = 0; i < length; i++) {
      var index = i;
      var part = parts[i];
      if (part.startsWith(":")) {
        var key = part.substring(1);
        var c = consumer;
        consumer = (components, map) -> { c.accept(components, map); map.put(key, components[index]); };
      } else {
        predicate = predicate.and(components -> part.equals(components[index]));
      }
    }

    var p =  predicate;
    var c = consumer;
    return components -> {
      if (!p.test(components)) {
        //System.err.println("do not match " + Arrays.toString(components));
        return Optional.empty();
      }
      var map = new HashMap<String,String>();
      c.accept(components, map);
      //System.err.println("match " + Arrays.toString(components) + " " + map);
      return Optional.of(map);
    };
  }

  private JExpress() {
    // empty
  }

  /**
   * Creates an Express like application.
   * @return a new Express like application.
   */
  public static JExpress express() {
    return new JExpress();
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

    private record Token(Kind kind, String text, int location) {
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
            .map(Kind::name).collect(joining(", ")) + " but recognized " + kind + " at " + location);
      }
    }

    private record Lexer(Matcher matcher) {
      private Token next() {
        for(;;) {
          if (!matcher.find()) {
            throw new IllegalStateException("no token recognized");
          }
          var index = rangeClosed(1, matcher.groupCount()).filter(i -> matcher.group(i) != null).findFirst().orElseThrow();
          var kind = Kind.VALUES[index - 1];
          if (kind != Kind.BLANK) {
            return new Token(kind, matcher.group(index), matcher.start(index));
          }
        }
      }
    }

    private static final Pattern PATTERN = compile(Arrays.stream(Kind.VALUES)
        .map(k -> k.regex).collect(joining("|")));

    /**
     * Parse a JSON text.
     *
     * @param input a JSON text
     * @return a Java object corresponding to the text
     */
    public static Object parse(String input) {
      var lexer = new Lexer(PATTERN.matcher(input));
      try {
        return parse(lexer);
      } catch(IllegalStateException e) {
        throw new IllegalStateException(e.getMessage() + "\n while parsing " + input, e);
      }
    }

    private static Object parse(Lexer lexer) {
      var token = lexer.next();
      return switch(token.kind) {
        case LEFT_CURLY -> {
          var object = new HashMap<String, Object>();
          parseObject(lexer, object);
          yield object;
        }
        case LEFT_BRACKET -> {
          var array = new ArrayList<>();
          parseArray(lexer, array);
          yield array;
        }
        default -> throw token.error(Kind.LEFT_CURLY, Kind.LEFT_BRACKET);
      };
    }

    private static void parseObjectValue(Token token, Lexer lexer, Map<String, Object> jsonObject, String key) {
      switch (token.kind) {
        case NULL -> jsonObject.put(key, null);
        case FALSE -> jsonObject.put(key, false);
        case TRUE -> jsonObject.put(key, true);
        case INTEGER -> jsonObject.put(key, parseInt(token.text));
        case DOUBLE -> jsonObject.put(key, parseDouble(token.text));
        case STRING -> jsonObject.put(key, token.text);
        case LEFT_CURLY -> {
          var map = new HashMap<String, Object>();
          parseObject(lexer, map);
          jsonObject.put(key, map);
        }
        case LEFT_BRACKET -> {
          var list = new ArrayList<Object>();
          parseArray(lexer, list);
          jsonObject.put(key, list);
        }
        default -> throw token.error(Kind.NULL, Kind.FALSE, Kind.TRUE, Kind.INTEGER, Kind.DOUBLE, Kind.STRING, Kind.LEFT_BRACKET, Kind.RIGHT_CURLY);
      }
    }

    private static void parseArrayValue(Token token, Lexer lexer, List<Object> jsonArray) {
      switch (token.kind) {
        case NULL -> jsonArray.add(null);
        case FALSE -> jsonArray.add(false);
        case TRUE -> jsonArray.add(true);
        case INTEGER -> jsonArray.add(parseInt(token.text));
        case DOUBLE -> jsonArray.add(parseDouble(token.text));
        case STRING -> jsonArray.add(token.text);
        case LEFT_CURLY -> {
          var map = new HashMap<String, Object>();
          parseObject(lexer, map);
          jsonArray.add(map);
        }
        case LEFT_BRACKET -> {
          var list = new ArrayList<>();
          parseArray(lexer, list);
          jsonArray.add(list);
        }
        default -> throw token.error(Kind.NULL, Kind.FALSE, Kind.TRUE, Kind.INTEGER, Kind.DOUBLE, Kind.STRING, Kind.LEFT_BRACKET, Kind.RIGHT_CURLY);
      }
    }

    private static void parseObject(Lexer lexer, Map<String, Object> jsonObject) {
      var token = lexer.next();
      if (token.is(Kind.RIGHT_CURLY)) {
        return;
      }
      for(;;) {
        var key = token.expect(Kind.STRING);
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
      var token = lexer.next();
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
      if (o instanceof Collection<?> collection) {
        return toJSONArray(collection.stream());
      }
      if (o instanceof Iterable<?> iterable) {
        return toJSONArray(StreamSupport.stream(spliteratorUnknownSize(iterable.iterator(), 0), false));
      }
      if (o instanceof Stream<?> stream) {
        return toJSONArray(stream);
      }
      if (o instanceof Map<?,?> map) {
        return toJSONObject(map);
      }
      if (o instanceof Record record) {
        return toJSONObject(record);
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
      return stream.map(JSONPrettyPrinter::toJSONItem).collect(joining(", ", "[", "]"));
    }
    private static String toJSONObject(Map<?,?> map) {
      return map.entrySet().stream()
          .map(e -> "\"" + e.getKey() + "\": " + toJSONItem(e.getValue()))
          .collect(joining(", ", "{", "}"));
    }
    private static Object accessor(Method accessor, Record record) {
      try {
        return accessor.invoke(record);
      } catch (IllegalAccessException e) {
        throw (IllegalAccessError) new IllegalAccessError().initCause(e);
      } catch (InvocationTargetException e) {
        var cause = e.getCause();
        if (cause instanceof RuntimeException runtimeException) {
          throw runtimeException;
        }
        if (cause instanceof Error error) {
          throw error;
        }
        throw new UndeclaredThrowableException(cause);
      }
    }
    private static String toJSONObject(Record record) {
      return Arrays.stream(record.getClass().getRecordComponents())
          .map(c -> "\"" + c.getName() + "\": " + toJSONItem(accessor(c.getAccessor(), record)))
          .collect(joining(", ", "{", "}"));
    }
  }

  private static final class VirtualThreadExecutor implements Executor {
    private static class BTB {
      private String name;
      private long counter;
      private int characteristics;
      private UncaughtExceptionHandler uhe;
    }
    private static class VTB extends BTB {
      private Executor executor;
    }

    private static final MethodHandle SET_EXECUTOR, OF_VIRTUAL, BUILDER_START;
    static {
      try {
        var unsafeClass = Class.forName("sun.misc.Unsafe");
        var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = unsafeField.get(null);
        var objectFieldOffset = unsafeClass.getMethod("objectFieldOffset", Field.class);
        var executorField = VTB.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        var executorOffset = (long) objectFieldOffset.invoke(unsafe, executorField);
        var putObject = MethodHandles.lookup()
            .findVirtual(unsafeClass, "putObject", methodType(void.class, Object.class, long.class, Object.class));
        SET_EXECUTOR = insertArguments(insertArguments(putObject, 2, executorOffset), 0, unsafe);

        var ofVirtualClass = Class.forName("java.lang.Thread$Builder$OfVirtual");
        OF_VIRTUAL = publicLookup().findStatic(Thread.class, "ofVirtual", methodType(ofVirtualClass))
            .asType(methodType(Object.class));

        BUILDER_START = publicLookup().findVirtual(ofVirtualClass, "start", methodType(Thread.class, Runnable.class))
            .asType(methodType(void.class, Object.class, Runnable.class));

      } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
        throw new AssertionError(e);
      }
    }

    private final Executor executor;

    public VirtualThreadExecutor(Executor executor) {
      this.executor = executor;
    }

    @Override
    public void execute(Runnable command) {
      try {
        var builder = OF_VIRTUAL.invokeExact();
        SET_EXECUTOR.invokeExact(builder, (Object) executor);
        BUILDER_START.invokeExact(builder, command);
      } catch (Throwable e) {
        e.printStackTrace(System.err);
        throw new AssertionError(e);
      }
    }
  }

  private static final Executor EXECUTOR;
  static {
    Executor executor;
    try {
      var ofVirtualClass = Class.forName("java.lang.Thread$Builder$OfVirtual");
      var ofVirtual = publicLookup().findStatic(Thread.class, "ofVirtual", methodType(ofVirtualClass));
      try {
        ofVirtual.invoke();
        executor = new VirtualThreadExecutor(Executors.newSingleThreadExecutor());
      } catch(UnsupportedOperationException e) {
        out.println("WARNING: Virtual threads are not enabled, use --enable-preview");
        executor = null;
      } catch(Throwable t) {
        throw new AssertionError(t);
      }
    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
      executor = null;
    }
    EXECUTOR = executor;
  }

  @FunctionalInterface
  private interface Pipeline {
    void accept(RequestImpl request, ResponseImpl response) throws IOException;
  }

  private Pipeline pipeline = (request, response) -> {
    var message = "no match " + request.method() + " " + request.path();
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
    var oldPipeline = pipeline;
    var matcher = matcher(path);
    pipeline = (request, response) -> {
      var paramsOpt = matcher.apply(request.components);
      if (paramsOpt.isPresent()) {
        request.exchange.setAttribute("params", paramsOpt.orElseThrow());
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
      var path = Path.of(root.toString(), request.path());
      response.sendFile(path);
    };
  }

  /**
   * Starts a server on the given port and listen for connections.
   * @param port a TCP port
   * @return the server instance
   * @throws UncheckedIOException if an I/O error occurs when creating the server.
   */
  public Server listen(int port) {
    HttpServer server;
    try {
      server = HttpServer.create(new InetSocketAddress(port), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext("/", exchange -> {
      System.err.println("request " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
      try {
        var components = exchange.getRequestURI().getPath().split("/");
        exchange.setAttribute("status", 200);
        pipeline.accept(new RequestImpl(exchange, components), new ResponseImpl(exchange));
        //exchange.close();
      } catch(Exception e) {
        e.printStackTrace();
        throw e;
      }
    });
    server.setExecutor(EXECUTOR);
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
    var app = express();
    app.use(staticFiles(Path.of(".")));
    app.listen(8080);

    out.println("application started on port 8080");
  }
}
