# Swagger support for Javalin (WIP)

Use extension method (for Kotlin) or static method (for Java) `serveSwagger()` to compile swagger file from Javalin
routes and serve it. The file is served from `/swagger/yaml/`. Optionally, you can enable Swagger UI, which will be
served from root path.

General configuration: TBD
UI configuration: TBD

Wrap handlers with `documented` method to attach additional metadata to routes.

API examples: TBD when finalized

See [Kotlin example](src/test/kotlin/io/javalin/swagger/ApiTest.kt)