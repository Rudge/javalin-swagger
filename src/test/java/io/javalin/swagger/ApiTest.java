package io.javalin.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

public class ApiTest {

    public static void main(String... args) {

        OpenAPI api = new OpenAPI()
            .info(new Info().title("Test"));

//        Javalin app = Javalin.create()
//            .port(8080)
//            .enableCorsForAllOrigins()
//            .post("test/:id", documented(route()
//                .param(
//                    new Parameter()
//                        .name("id")
//                        .in("path")
//                        .allowEmptyValue(true)), ctx -> {
//
//            }))
//            .get("test", documented(route(), ctx -> { }));

//        serveSwagger(app, api).start();
    }
}
