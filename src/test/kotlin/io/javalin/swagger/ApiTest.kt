package io.javalin.swagger

import io.javalin.Javalin
import io.javalin.swagger.Component.EXTERNAL
import io.javalin.swagger.DocumentedHandler.documented
import io.javalin.swagger.annotations.Property
import io.swagger.v3.oas.annotations.enums.ParameterIn.PATH
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info

fun main(args: Array<String>) {

    val api = OpenAPI()
        .info(
            Info()
                .title("Test")
        )

    Javalin.create()
        .port(8080)
        .enableCorsForAllOrigins()
        .post("test/:id", documented(
            route()
                .description("test")
                .params {
                    parameter("id", PATH)
                }
                .response()
                .add(
                    withStatus(200)
                        .content(
                            content()
                                .entry(
                                    withMime("application/html")
                                        .schema(String::class.java)
                                        .example("<h1>Hello!</h1>")
                                )
                        )
                )
                .add(
                    withStatus(404)
                        .content(
                            content()
                                .entry(
                                    withMime("application/json")
                                        .schema(TestError::class.java)
                                        .example(TestError("Example", EXTERNAL))
                                )
                        )
                )
                .build()
        ) { })
        .get("test", documented(route()) { })
        .serveSwagger(api)
        .start()
}

data class TestError(
    @Property
    val cause: String,
    @Property
    val component: Component
)

enum class Component(val value: String) {
    INTERNAL("internal"), EXTERNAL("external")
}
