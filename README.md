# jexpress
A light [http://expressjs.com/](express.js) like framework written in Java (in one file)

methods on JExpress: [https://rawgit.com/forax/jexpress/master/doc/JExpress.html#express--]express(), get(), post(), put(), delete() and listen()
methods on Request: body(), method(), param(), path()
methods on Response: status(), type(), set(), append(), json(), send()

- Example
  ```java
  public static void main(String[] args) throws IOException {

    JExpress app = express();

    app.get("/foo/:id", (req, res) -> {
      String id = req.param("id");
      res.send("<html><p>id =" + id + "</p></html>");
    });

    app.listen(3000);
  }
  ```

- Compile the application with
  ```
  cd src
  javac JExpress.java
  ```
  
- Run the application with
  ```
  cd src
  java JExpress
  ```
  
- Generate the documentation with
  ```
  cd src
  javadoc -d ../doc JExpress.java
  ```
  
 The documentation with rawgit [https://rawgit.com/forax/jexpress/master/doc/index.html].
 