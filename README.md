# jexpress
A light and slow [express.js](http://expressjs.com/) like framework written in Java 17 (in one file)

## API
- JExpress: [express()](https://rawgit.com/forax/jexpress/master/doc/JExpress.html#express--),
            [get(path, callback)](https://rawgit.com/forax/jexpress/master/doc/JExpress.html#get-java.lang.String-JExpress.Callback-),
            [post(path, callback)](https://rawgit.com/forax/jexpress/master/doc/JExpress.html#post-java.lang.String-JExpress.Callback-),
            [put(path, callback)](https://rawgit.com/forax/jexpress/master/doc/JExpress.html#put-java.lang.String-JExpress.Callback-),
            [delete(path, callback)](https://rawgit.com/forax/jexpress/master/doc/JExpress.html#delete-java.lang.String-JExpress.Callback-) and
            [listen(port)](https://rawgit.com/forax/jexpress/master/doc/JExpress.html#listen-int-)                   
- Request: [body()](https://rawgit.com/forax/jexpress/master/doc/JExpress.Request.html#body--),
           [bodyText()](https://rawgit.com/forax/jexpress/master/doc/JExpress.Request.html#bodyText--),
           [method()](https://rawgit.com/forax/jexpress/master/doc/JExpress.Request.html#method--),
           [param(name)](https://rawgit.com/forax/jexpress/master/doc/JExpress.Request.html#param-java.lang.String-),
           [path()](https://rawgit.com/forax/jexpress/master/doc/JExpress.Request.html#path--)         
- Response: [status(status)](https://rawgit.com/forax/jexpress/master/doc/JExpress.Response.html#status-int-),
            [type(type)](https://rawgit.com/forax/jexpress/master/doc/JExpress.Response.html#type-java.lang.String-),
            [set(field, value)](https://rawgit.com/forax/jexpress/master/doc/JExpress.Response.html#set-java.lang.String-java.lang.String-),
            [append(field, value)](https://rawgit.com/forax/jexpress/master/doc/JExpress.Response.html#append-java.lang.String-java.lang.String-),
            [json(object)](https://rawgit.com/forax/jexpress/master/doc/JExpress.Response.html#json-java.lang.String-),
            [json(text)](https://rawgit.com/forax/jexpress/master/doc/JExpress.Response.html#json-java.lang.String-),
            [send(body)](https://rawgit.com/forax/jexpress/master/doc/JExpress.Response.html#send-java.lang.String-)

The full [javadoc](https://rawgit.com/forax/jexpress/master/doc/index.html)

## Example
  ```java
  public static void main(String[] args) throws IOException {

    var app = express();

    app.get("/foo/:id", (req, res) -> {
      var id = req.param("id");
      res.send("<html><p>id =" + id + "</p></html>");
    });

    app.listen(3000);
  }
  ```

## Build
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
 