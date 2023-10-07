# jexpress
[JExpress.java](src/main/java/JExpress.java), a light and slow [express.js](http://expressjs.com/) clone
written in Java 17 (in [one file](src/main/java/JExpress.java)).
It uses virtual threads if run with Java 21 (or Java 19/20 with --enable-preview).

There is also [JExpress8.java](src/main/java/JExpress8.java), a version backward compatible with Java 8.

## [API](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.html)
- JExpress: [express()](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.html#express()),
            [get(path, callback)](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.html#get(java.lang.String,JExpress.Callback)),
            [post(path, callback)](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.html#post(java.lang.String,JExpress.Callback)),
            [put(path, callback)](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.html#put(java.lang.String,JExpress.Callback)),
            [delete(path, callback)](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.html#delete(java.lang.String,JExpress.Callback)),
            [use(path, handler)](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.html#use(java.lang.String,JExpress.Handler)),
            [listen(port)](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.html#listen(int)) and
            [staticFiles(root)](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.html#staticFiles(java.nio.file.Path)).
- Request: [bodyArray()](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.Request.html#bodyArray()),
           [bodyObject()](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.Request.html#bodyObject()),
           [bodyText()](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.Request.html#bodyText()),
           [get(header)](https://javadoc.jitpack.io/com/github/forax/jexpress/master-97364cec98-1/javadoc/JExpress.Request.html#get(java.lang.String)),
           [method()](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.Request.html#method()),
           [param(name)](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.Request.html#param(java.lang.String)) and
           [path()](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.Request.html#path()).
- Response: [status(status)](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.Response.html#status(int)),
            [type(type, charset)](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.Response.html#type(java.lang.String,java.nio.charset.Charset)),
            [set(field, value)](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.Response.html#set(java.lang.String,java.lang.String)),
            [append(field, value)](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.Response.html#append(java.lang.String,java.lang.String)),
            [json(object)](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.Response.html#json(java.lang.Object)),
            [send(body)](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.Response.html#send(java.lang.String)) and
            [sendFile(path)](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.Response.html#sendFile(java.nio.file.Path)).

The full [javadoc](https://javadoc.jitpack.io/com/github/forax/jexpress/master-SNAPSHOT/javadoc/JExpress.html)

## Example
  ```java
  public static void main(String[] args) throws IOException {

    var app = express();

    app.use(staticFiles(Path.of("public")));

    app.get("/hello/:id", (req, res) -> {
      var id = req.param("id");
      record Hello(String id) {}
      res.json(new Hello(id));
    });
    
    app.get("/LICENSE", (req, res) -> {
      res.sendFile(Paths.get("LICENSE"));
    });

    app.listen(3000);
  }
  ```

## Run and test
- Run the application with
  ```
  cd src/main/java
  java JExpress.java
  ```

- Test the application with Maven
  ```
  mvn clean package
  ```

 