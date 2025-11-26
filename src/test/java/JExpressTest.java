import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Execution(ExecutionMode.CONCURRENT)
public class JExpressTest {
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();

  private static HttpResponse<String> fetchGet(int port, String uri) throws IOException, InterruptedException {
    var request = HttpRequest.newBuilder().uri(URI.create("http://localhost" + ":" + port + uri)).build();
    return HTTP_CLIENT.send(request, BodyHandlers.ofString());
  }

  private static HttpResponse<String> fetchJSONPost(int port, String uri, String jsonBody) throws IOException, InterruptedException {
    var request = HttpRequest.newBuilder().uri(URI.create("http://localhost" + ":" + port + uri))
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
        .header("Content-Type", "application/json")
        .build();
    return HTTP_CLIENT.send(request, BodyHandlers.ofString());
  }

  private static JExpress express() {
    return JExpress.express();
  }

  private static final AtomicInteger PORT = new AtomicInteger(5_25_00);

  private static int nextPort() {
    return PORT.getAndIncrement();
  }

  @Test
  public void testJSONObjectRecord() throws IOException, InterruptedException {
    var app = express();
    app.get("/hello/:id", (req, res) -> {
      var id = req.param("id");
      record Hello(String id) {}
      res.json(new Hello(id));
    });

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetchGet(port, "/hello/42");
      var body = response.body();
      assertAll(
          () -> assertEquals("application/json; charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow()),
          () -> assertEquals("""
                    {"id": "42"}\
                    """, body)
      );
    }
  }

  @Test
  public void testJSONObjectMap() throws IOException, InterruptedException {
    var app = express();
    app.get("/json-object", (req, res) -> {
      res.json(new LinkedHashMap<String, Object>() {{ put("foo", 4); put("bar", null); }});
    });

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetchGet(port, "/json-object");
      var body = response.body();
      assertAll(
          () -> assertEquals("application/json; charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow()),
          () -> assertEquals("""
                    {"foo": 4, "bar": null}\
                    """, body)
      );
    }
  }

  @Test
  public void testJSONArray() throws IOException, InterruptedException {
    var app = express();
    app.get("/json-array", (req, res) -> {
      res.json(List.of(true, 3, 4.5, "foo"));
    });

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetchGet(port, "/json-array");
      var body = response.body();
      assertAll(
          () -> assertEquals("application/json; charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow()),
          () -> assertEquals("""
                    [true, 3, 4.5, "foo"]\
                    """, body)
      );
    }
  }

  @Test
  public void testJSONArrayStream() throws IOException, InterruptedException {
    var app = express();
    app.get("/json-stream", (req, res) -> {
      res.json(Stream.of(true, 3, 4.5, "foo"));
    });

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetchGet(port, "/json-stream");
      var body = response.body();
      assertAll(
          () -> assertEquals("application/json; charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow()),
          () -> assertEquals("""
                    [true, 3, 4.5, "foo"]\
                    """, body)
      );
    }
  }

  @Test
  public void testLicense() throws IOException, InterruptedException {
    var app = express();
    app.get("/LICENSE", (req, res) -> {
      res.sendFile(Path.of("LICENSE"));
    });

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetchGet(port, "/LICENSE");
      var body = response.body();
      assertAll(
          () -> assertEquals(1067, body.length()),
          () -> assertEquals("""
              MIT License
                                  
              Copyright (c) 2017 Remi Forax
                                  
              Permission is hereby granted, free of charge, to any person obtaining a copy\
              """, body.lines().limit(5).collect(joining("\n")))
      );
    }
  }

  @Test
  public void testStaticFile() throws IOException, InterruptedException {
    var app = express();
    app.use(JExpress.staticFiles(Path.of(".")));

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetchGet(port, "/LICENSE");
      var body = response.body();
      assertAll(
          () -> assertEquals(1067, body.length()),
          () -> assertEquals("""
              MIT License
                                  
              Copyright (c) 2017 Remi Forax
                                  
              Permission is hereby granted, free of charge, to any person obtaining a copy\
              """, body.lines().limit(5).collect(joining("\n")))
      );
    }
  }

  @Test
  public void testStaticFileHTMLContentType() throws IOException, InterruptedException {
    var app = express();
    app.use(JExpress.staticFiles(Path.of("./src/test/resources")));

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetchGet(port, "/foo.html");
      var body = response.body();
      var contentType = response.headers().firstValue("Content-Type").orElseThrow();
      assertAll(
          () -> assertEquals("text/html; charset=utf-8", contentType),
          () -> assertEquals(72, body.length()),
          () -> assertEquals("""
              <!DOCTYPE html>
              <html>
               <body>
                 <!-- a HTML file -->
               </body>
              </html>
              """, body)
      );
    }
  }

  @Test
  public void testStaticFileCSSContentType() throws IOException, InterruptedException {
    var app = express();
    app.use(JExpress.staticFiles(Path.of("./src/test/resources")));

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetchGet(port, "/foo.css");
      var body = response.body();
      var contentType = response.headers().firstValue("Content-Type").orElseThrow();
      assertAll(
          () -> assertEquals("text/css; charset=utf-8", contentType),
          () -> assertEquals(25, body.length()),
          () -> assertEquals("""
              /* a CSS file */
              DIV {
              }
              """, body)
      );
    }
  }

  @Test
  public void testStaticFileJSContentType() throws IOException, InterruptedException {
    var app = express();
    app.use(JExpress.staticFiles(Path.of("./src/test/resources")));

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetchGet(port, "/foo.js");
      var body = response.body();
      var contentType = response.headers().firstValue("Content-Type").orElseThrow();
      assertAll(
          () -> assertTrue(contentType.equals("text/javascript; charset=utf-8") || contentType.equals("application/javascript")),
          () -> assertEquals(28, body.length()),
          () -> assertEquals("""
              "use strict";
               
              // a js file
              """, body)
      );
    }
  }

  @Test
  public void testStaticFileJSONContentType() throws IOException, InterruptedException {
    var app = express();
    app.use(JExpress.staticFiles(Path.of("./src/test/resources")));

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetchGet(port, "/foo.json");
      var body = response.body();
      var contentType = response.headers().firstValue("Content-Type").orElseThrow();
      assertAll(
          () -> assertEquals("application/json", contentType),
          () -> assertEquals(36, body.length()),
          () -> assertEquals("""
              [true, 1, 3.14, "foo", { "a": 14 }]
              """, body)
      );
    }
  }

  @Test @Disabled
  public void testVirtualThread() throws IOException, InterruptedException {
    var app = express();
    app.get("/", (req, res) -> {
      res.send(Thread.currentThread().toString());
    });

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetchGet(port, "/virtualThread");
      var body = response.body();
      assertAll(
          () -> assertTrue(body.contains("/")),
          () -> assertTrue(body.startsWith("VirtualThread"))
      );
    }
  }

  @Test @Disabled
  public void testVirtualThreadSeveralRequests() throws IOException, InterruptedException {
    var app = express();
    app.get("/", (req, res) -> {
      res.send(Thread.currentThread().toString());
    });

    var port = nextPort();
    try(var server = app.listen(port)) {
      for(var i = 0; i < 1_000; i++) {
        var response = fetchGet(port, "/virtualThread");
        var body = response.body();
        System.err.println(body);
        assertAll(
            () -> assertTrue(body.contains("/")),
            () -> assertTrue(body.startsWith("VirtualThread"))
        );
      }
    }
  }

  @Test
  public void testJSONObjectPost() throws IOException, InterruptedException {
    var app = express();
    app.post("/user", (request, response) -> {
      var body = request.bodyObject();
      response.json(body);
    });

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetchJSONPost(port, "/user", """
          {
            "user": "Bob"
          }
          """);
      var body = response.body();
      var contentType = response.headers().firstValue("Content-Type").orElseThrow();
      assertAll(
          () -> assertEquals("application/json; charset=utf-8", contentType),
          () -> assertEquals(15, body.length()),
          () -> assertEquals("""
              {"user": "Bob"}\
              """, body)
      );
    }
  }

  @Test
  public void testJSONArrayPost() throws IOException, InterruptedException {
    var app = express();
    app.post("/user", (request, response) -> {
      var body = request.bodyArray();
      response.json(body);
    });

    var port = nextPort();
    try (var server = app.listen(port)) {
      var response = fetchJSONPost(port, "/user", """
          [null, false, true, 3, 4.5, "foo"]
          """);
      var body = response.body();
      var contentType = response.headers().firstValue("Content-Type").orElseThrow();
      assertAll(
          () -> assertEquals("application/json; charset=utf-8", contentType),
          () -> assertEquals(34, body.length()),
          () -> assertEquals("""
              [null, false, true, 3, 4.5, "foo"]\
              """, body)
      );
    }
  }

  @Test
  public void testJSONObjectAndArrayPost() throws IOException, InterruptedException {
    var app = express();
    app.post("/user", (request, response) -> {
      var body = request.bodyObject();
      response.json(body);
    });

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetchJSONPost(port, "/user", """
          {
            "values": [null, false, true, 3, 4.5, "foo"]
          }
          """);
      var body = response.body();
      var contentType = response.headers().firstValue("Content-Type").orElseThrow();
      assertAll(
          () -> assertEquals("application/json; charset=utf-8", contentType),
          () -> assertEquals(46, body.length()),
          () -> assertEquals("""
              {"values": [null, false, true, 3, 4.5, "foo"]}\
              """, body)
      );
    }
  }
}