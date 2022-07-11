import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

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

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JExpressLoomTest {
  private static final boolean ENABLE_PREVIEW_AND_LOOM;
  static {
    var enablePreviewAndLoom = false;
    try {
      var ofVirtualClass = Class.forName("java.lang.Thread$Builder$OfVirtual");
      var ofVirtual = publicLookup().findStatic(Thread.class, "ofVirtual", methodType(ofVirtualClass));
      try {
        ofVirtual.invoke();
        enablePreviewAndLoom = true;
      } catch(UnsupportedOperationException e) {
        // enable preview not enabled !
      }
    } catch(RuntimeException | Error e) {
      throw e;
    } catch(ClassNotFoundException e) {
      // not JDK 19
    } catch(Throwable e) {
      throw new AssertionError(e);
    }
    ENABLE_PREVIEW_AND_LOOM = enablePreviewAndLoom;
  }

  public static boolean enablePreviewAndLoom() {
    return ENABLE_PREVIEW_AND_LOOM;
  }

  private static HttpResponse<String> fetch(int port, String uri) throws IOException, InterruptedException {
    var client = HttpClient.newBuilder().build();
    var request = HttpRequest.newBuilder().uri(URI.create("http://localhost" + ":" + port + uri)).build();
    return client.send(request, BodyHandlers.ofString());
  }

  private static JExpressLoom express() {
    return JExpressLoom.express();
  }

  private static final AtomicInteger PORT = new AtomicInteger(5_19_00);

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
      var response = fetch(port, "/hello/42");
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
      var response = fetch(port, "/json-object");
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
      var response = fetch(port, "/json-array");
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
      var response = fetch(port, "/json-stream");
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
      var response = fetch(port, "/LICENSE");
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
    app.use(JExpressLoom.staticFiles(Path.of(".")));

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetch(port, "/LICENSE");
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
    app.use(JExpressLoom.staticFiles(Path.of("./src/test/resources")));

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetch(port, "/foo.html");
      var body = response.body();
      assertAll(
          () -> assertEquals("text/html; charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow()),
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
    app.use(JExpressLoom.staticFiles(Path.of("./src/test/resources")));

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetch(port, "/foo.css");
      var body = response.body();
      assertAll(
          () -> assertEquals("text/css; charset=utf-8", response.headers().firstValue("Content-Type").orElseThrow()),
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
    app.use(JExpressLoom.staticFiles(Path.of("./src/test/resources")));

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetch(port, "/foo.js");
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
  @EnabledIf("enablePreviewAndLoom")
  public void testLoom() throws IOException, InterruptedException {
    var app = express();
    app.get("/", (req, res) -> {
      res.send(Thread.currentThread().toString());
    });

    var port = nextPort();
    try(var server = app.listen(port)) {
      var response = fetch(port, "/virtualThread");
      var body = response.body();
      assertAll(
          () -> assertTrue(body.contains("/")),
          () -> assertTrue(body.startsWith("VirtualThread"))
      );
    }
  }
}