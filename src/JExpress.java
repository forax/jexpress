import static java.util.stream.Collectors.joining;
import static java.lang.System.out;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * An express.js-like application framework
 * 
 * Compile the application with : javac JExpress.java
 * Run the application with     : java JExpress
 * Get the documentation with   : javadoc -d ../doc JExpress.java
 */
@SuppressWarnings("restriction")
public class JExpress {
  /**
   * A HTTP request
   */
  public interface Request {
    @Deprecated
    HttpExchange exchange();
    
    /**
     * The HTTP method of the request.
     * @return the HTTP method.
     */
    default String method() {
      return exchange().getRequestMethod().toUpperCase();
    }
    
    /**
     * The HTTP path of the request.
     * @return the HTTP path.
     */
    default String path() {
      return exchange().getRequestURI().getPath();
    }
    
    /**
     * Get named route parameter
     * @param name name of the parameter
     * @return the value of the parameter or ""
     */
    @SuppressWarnings("unchecked")
    default String param(String name) {
      return ((Map<String, String>)exchange().getAttribute("params")).getOrDefault(name, "");
    }
    
    /**
     * Returns the body of the request
     * @return the body of the request
     */
    default InputStream body() {
      return exchange().getRequestBody();
    }
    
    /**
     * Returns the body of the request as a String.
     * @return the body of the request as a String.
     * @throws IOException if an I/O error occurs.
     */
    default String bodyText() throws IOException {
      try(InputStream in = exchange().getRequestBody();
          InputStreamReader reader = new InputStreamReader(in);
          BufferedReader buffered = new BufferedReader(reader)) {
        return buffered.lines().collect(joining("\n"));
      }
    }
  }
  
  /**
   * A HTTP response
   */
  public interface Response {
    @Deprecated
    HttpExchange exchange();
    
    /**
     * Set the HTTP status of the response
     * @param status the HTTP status (200, 404, etc)
     * @return the current response.
     */
    default Response status(int status) {
      exchange().setAttribute("status", status);
      return this;
    }
    
    /**
     * Appends the specified value to the HTTP response header field.
     * @param field a header field name
     * @param value the value of the header field
     * @return the current response.
     */
    default Response append(String field, String value) {
      exchange().getResponseHeaders().add(field, value);
      return this;
    }
    
    /**
     * Sets the response’s HTTP header field to value
     * @param field a header field name
     * @param value the value of the header field
     * @return the current response.
     */
    default Response set(String field, String value) {
      exchange().getResponseHeaders().set(field, value);
      return this;
    }
    
    /**
     * Sets the Content-Type HTTP header to the MIME type.
     * @param type the MIME type.
     * @return the current response.
     */
    default Response type(String type) {
      return set("Content-Type", type);
    }
    
    /**
     * Sends a JSON response with the correct 'Content-Type'.
     * @param stream a stream of Object, toString will be called on each of them.
     * @throws IOException if an I/O error occurs.
     */
    default void json(Stream<?> stream) throws IOException {
      json(stream.map(Object::toString).collect(joining(", ", "[", "]")));
    }
    
    /**
     * Sends a JSON response with the correct 'Content-Type'.
     * @param json a JSON string
     * @throws IOException if an I/O error occurs.
     */
    default void json(String json) throws IOException {
      type("application/json");
    }
    
    /**
     * Send a Text response.
     * If the status is not defined 200 will be used. 
     * If the Content-Type is not defined, "text/html" will be used.
     * @param body the text of the response
     * @throws IOException if an I/O error occurs.
     */
    default void send(String body) throws IOException {
      byte[] content = body.getBytes("UTF8");
      Integer statusAttr = (Integer)exchange().getAttribute("status");
      int status = (statusAttr == null)? 200: statusAttr;
      exchange().sendResponseHeaders(status, content.length);
      Headers headers = exchange().getResponseHeaders();
      if (!headers.containsKey("Content-Type")) {
        type("text/html");
      }
      exchange().getResponseBody().write(content);
    }
  }
  
  /**
   * A callback called to process a HTTP request in order to
   * create a HTTP response.
   */
  @FunctionalInterface
  public interface Callback {
    /**
     * Called to process a HTTP request in order to create a HTTP response.
     * @param request a HTTP request
     * @param response a HTTP response
     * @throws IOException if an I/O occurs
     */
    public void accept(Request request, Response response) throws IOException;
  }
  
  private static BiConsumer<Request, Response> unchecked(Callback callback) {
    return (request, response) -> {
      try {
        callback.accept(request, response);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
  }
  
  private static Function<String, Optional<Map<String, String>>> matcher(String uri) {
    String[] parts = uri.split("/");
    int length = parts.length;
    Predicate<String[]> predicate =  cs -> cs.length >= length;
    BiConsumer<String[], Map<String,String>> consumer = (_1, _2) -> { /* empty */ };
    for(int i = 0; i < length; i++) {
      int index = i;
      String part = parts[i];
      if (part.startsWith(":")) {
        String key = part.substring(1);
        BiConsumer<String[], Map<String,String>> c = consumer;
        consumer = (cs, map) -> { c.accept(cs, map); map.put(key, cs[index]); };
      } else {
        predicate = predicate.and(cs -> part.equals(cs[index]));
      }
    }
    
    Predicate<String[]> p =  predicate;
    BiConsumer<String[], Map<String,String>> c = consumer;
    return s -> {
      String[] components = s.split("/");
      if (!p.test(components)) {
        return Optional.empty();
      }
      
      HashMap<String,String> map = new HashMap<>();
      c.accept(components, map);
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
  
  private Callback callback = (request, response) -> {
    System.err.println("no match " + request.method() + " " + request.path());
    response.status(404).send("<html><h1>No Match</h1></html>");
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
    Callback oldCallback = this.callback;
    Function<String, Optional<Map<String, String>>> matcher = matcher(path);
    this.callback = (request, response) -> {
      try {
        //TODO avoid ifPresent/get when upgrade to Java 9
        Optional<Map<String,String>> params = Optional.of(request)
          .filter(req -> req.method().equalsIgnoreCase(method))
          .flatMap(matcher.compose(Request::path));
        if (params.isPresent()) {
          request.exchange().setAttribute("params", params.get());
          unchecked(callback).accept(request, response);
        } else {
          unchecked(oldCallback).accept(request, response);
        }
      } catch(UncheckedIOException e) {
        throw e.getCause();
      }
    };
  }   
  
  /**
   * Starts a server on the given port and listen for connections.
   * @param port a TCP port
   * @throws IOException if an I/O error occurs.
   */
  public void listen(int port) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/", exchange -> {
      callback.accept(() -> exchange, () -> exchange);
      exchange.close();
    });
    server.setExecutor(null);
    server.start();
  }

  

  // ---------------------------------------------------------- //
  //  DO NOT EDIT ABOVE THIS LINE                               //
  // ---------------------------------------------------------- //
  
  public static void main(String[] args) throws IOException {

    JExpress app = express();

    app.get("/foo/:id", (req, res) -> {
      String id = req.param("id");
      res.send("<html><p>id =" + id + "</p></html>");
    });

    app.listen(3000);
    
    out.println("application started on port 3000");
  }
}
