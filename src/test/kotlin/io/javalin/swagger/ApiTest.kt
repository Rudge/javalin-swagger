package io.javalin.swagger

import io.javalin.Javalin
import io.javalin.swagger.DocumentedHandler.documented
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.parameters.Parameter

fun main(args: Array<String>) {

    val api = OpenAPI()
            .info(Info().title("Test"))

    Javalin.create()
        .port(8080)
        .enableCorsForAllOrigins()
        .post("test/:id", documented(route()
            .addParametersItem(
                Parameter()
                    .name("id")
                    .`in`("path")
                    .allowEmptyValue(true))) {

        })
        .get("test", documented(route()) { })
        .serveSwagger(api)
        .start()
}
