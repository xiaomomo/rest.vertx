# rest.vertx
A JAX-RS (RestEasy) like annotation processor for vert.x verticals
 
## Setup
```xml
<dependency>      
     <groupId>com.zandero</groupId>      
     <artifactId>rest.vertx</artifactId>      
     <version>0.1</version>      
</dependency>
```

## Example
**Step 1** - annotate a class with JAX-RS annotations 
```java
@Path("/test")
public class TestRest {

	@GET
	@Path("/echo")
	@Produces(MediaType.TEXT_HTML)
	public String echo() {

		return "Hello world!";
	}
}


```
**Step 2** - register annotated class as REST API
```java
TestRest rest = new TestRest();
Router router = RestRouter.register(vertx, rest);

vertx.createHttpServer()
		.requestHandler(router::accept)
		.listen(PORT);
```

## Paths
Each class can be annotated with a root (or base) path @Path("/rest")

Following that each public method must have a @Path annotation in order to be registered as a REST endpoint. 

### Path variables
Both class and methods support @Path variables.

```java
// RestEasy path param style
@GET
@Path("/execute/{param}")
public String execute(@PathParam("param") String parameter) {
	return parameter;
}
```

```
GET /execute/that -> that
```

```java
// vert.x path param style
@GET
@Path("/execute/:param")
public String execute(@PathParam("param") String parameter) {
	return parameter;
}
```

```
GET /execute/this -> this
```

### Path regular expressions
```java
// RestEasy path param style with regular expression {parameter:>regEx<}
@GET
@Path("/{one:\\w+}/{two:\\d+}/{three:\\w+}")
public String oneTwoThree(@PathParam("one") String one, @PathParam("two") int two, @PathParam("three") String three) {
	return one + two + three;
}
```

```
GET /test/4/you -> test4you
```

**Not recoomended** but possible are vert.x style paths with regular expressions.  
In this case method parameters correspond to path expressions by index. 
```java
@GET
@Path("/\\d+/minus/\\d+")
public Response test(int one, int two) {
    return Response.ok(one - two).build();
}
```

```
GET /12/minus/3 -> 9
```

### Query variables
Query variables are defined using the @QueryParam annotation.  
In case method arguments are not _nullable_ they must be provided or a **400 bad request** response follows. 

```java
@Path("calculate")
public class CalculateRest {

	@GET
	@Path("add")
	public int add(@QueryParam("one") int one, @QueryParam("two") int two) {

		return one + two;
	}
}
```

```
GET /calculate/add/1/2 -> 3
```

### Conversion of path and query variables to Java objects 
Rest.Vertx tries to convert path and query variables to their corresponding Java types.
    
Basic (primitive) types are converted from string to given type - if conversion is not possible a **400 bad request** response follows.
 
Complex java objects are converted according to **@Consumes** annotation or **request body reader** associated.

**Option 1** - The @Consumes annotation mime/type defines the reader to be used when converting request body.  
In this case a build in JSON converter is applied.
```java
@Path("consume")
public class ConsumeJSON {

	@POST
	@Path("read")
	@Consumes("application/json")
	public String add(SomeClass item) {

		return "OK";
	}
}
```  

**Option 2** - The @RequestReader annotation defines a specific reader to be used when converting request body.
```java
@Path("consume")
public class ConsumeJSON {

	@POST
	@Path("read")
	@Consumes("application/json")
	@RequestReader(SomeClassReader.class)
	public String add(SomeClass item) {

		return "OK";
	}
}
```

**Option 3** - An RequestReader is globally assigned to a specific class type.

```java
RestRouter.getReaders().register(SomeClass.class, SomeClassReader.class);
```

```java
@Path("consume")
public class ConsumeJSON {

	@POST
	@Path("read")
	public String add(SomeClass item) {

		return "OK";
	}
}
```

**Option 4** - An RequestReader is globally assigned to a specific mime type.

```java
RestRouter.getReaders().register("application/json", SomeClassReader.class);
```

```java
@Path("consume")
public class ConsumeJSON {

	@POST
	@Path("read")
	@Consumes("application/json")
	public String add(SomeClass item) {

		return "OK";
	}
}
```

First appropriate reader is assigned searching in following order:
1. use assigned method RequestReader
1. use class type specific reader
1. use mime type assigned reader
1. use general purpose reader

### Cookies, forms and headers ...
Cookies, HTTP form and headers can also be read via @CookieParam, @HeaderParam and @FormParam annotations.  

```java
@Path("read")
public class TestRest {

	@GET
	@Path("cookie")
	public String readCookie(@CookieParam("SomeCookie") String cookie) {

		return cookie;
	}
}
```

```java
@Path("read")
public class TestRest {

	@GET
	@Path("header")
	public String readHeader(@HeaderParam("X-SomeHeader") String header) {

		return header;
	}
}
```

```java
@Path("read")
public class TestRest {

	@POST
	@Path("form")
	public String readForm(@FormParam("username") String user, @FormParam("password") String password) {

		return "User: " + user + ", is logged in!";
	}
}
```


## Request context
Additional request bound variables can be provided as method arguments using the @Context annotation.
 
Following types are by default supported:
* **HttpServerRequest** vert.x current request 
* **HttpServerResponse** vert.x response (of current request)
* **Vertx** vert.x instance
* **RoutingContext** vert.x routing context (of current request)
* **User** vert.x user entity (if set)
* **RouteDefinition** vertx.rest route definition (reflection of route annotation)

```java
@GET
@Path("/context")
public String createdResponse(@Context HttpServerResponse response, @Context HttpServerRequest request) {

	response.setStatusCode(201);
	return request.uri();
}
```

### Pushing a custom context
While processing a request a custom context can be pushed into the vert.x routing context data storage.  
This context data can than be utilized as a method argument.


In order to achieve this we need to create a custom handler that pushes the context before the REST endpoint is called:
```java
Router router = Router.router(vertx);
router.route().handler(pushContextHandler());

router = RestRouter.register(router, new CustomContextRest());
vertx.createHttpServer()
		.requestHandler(router::accept)
		.listen(PORT);

private Handler<RoutingContext> pushContextHandler() {

	return context -> {
		RestRouter.pushContext(context, new MyCustomContext("push this into storage"));
		context.next();
	};
}
```

Then the context object can than be used as a method argument 
```java
@Path("custom")
public class CustomContextRest {
	

    @GET
    @Path("/context")
    public String createdResponse(@Context MyCustomContext context) {
    
    }
```

## Response building

### Response writers
Metod results are converted using response writers.  
Response writers take the method result and produce a vert.x response.

**Option 1** - The @Produces annotation mime/type defines the writer to be used when converting response.  
In this case a build in JSON writer is applied.
```java
@Path("produces")
public class ConsumeJSON {

	@GET
	@Path("write")
	@Produces("application/json")
	public SomeClass write() {

		return new SomeClass();
	}
}
```

**Option 2** - The @ResponseWriter annotation defines a specific writer to be used.
```java
@Path("produces")
public class ConsumeJSON {

	@GET
	@Path("write")
	@Produces("application/json")
	@ResponseWriter(SomeClassWriter.class)
	public SomeClass write() {

		return new SomeClass();
	}
}
```

**Option 3** - An ResponseWriter is globally assigned to a specific class type.

```java
RestRouter.getWriters().register(SomeClass.class, SomeClassWriter.class);
```


**Option 4** - An ResponseWriter is globally assigned to a specific mime type.

```java
RestRouter.getWriters().register("application/json", MyJsonWriter.class);
```

```java
@Path("produces")
public class ConsumeJSON {

	@GET
	@Path("write")
	@Produces("application/json")
	public SomeClass write() {

		return new SomeClass();
	}
}
```

First appropriate writer is assigned searching in following order:
1. use assigned method ResponseWriter
1. use class type specific writer
1. use mime type assigned writer
1. use general purpose writer (call to _.toString()_ method of returned object)

### vert.x response builder
In order to manipulate response codes, cookies, headers ... we can utilize the @Context HttpServerResponse.
 
```java
@GET
@Path("/login")
public HttpServerResponse getRoute(@Context HttpServerResponse response) {

    response.setStatusCode(201);
    response.putHeader("X-MySessionHeader", sessionId);
    response.end("Hello world!");
    return reponse;
}
```

### JAX-RS response builder
**NOTE** in order to utilize the JAX Response.builder() an existing JAX-RS implementation must be provided.  
Vertx.rest uses the Glassfish Jersey implementation for testing: 
```
<dependency>
    <groupId>org.glassfish.jersey.core</groupId>
    <artifactId>jersey-common</artifactId>
    <version>${version.glassfish}</version>
    <scope>test</scope>
</dependency>
```

```java
@GET
@Path("/login")
public Response jax() {

    return Response
        .accepted("Hello world!!")
        .header("X-MySessionHeader", sessionId)
        .build();
}
```

## User roles & authorization