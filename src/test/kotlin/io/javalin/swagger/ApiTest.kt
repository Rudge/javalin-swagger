package io.javalin.swagger

import io.javalin.ApiBuilder.post
import io.javalin.Javalin
import io.javalin.swagger.DocumentedHandler.documented
import io.javalin.swagger.annotations.Property
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info

fun main(args: Array<String>) {

    val api = OpenAPI()
        .info(
            Info()
                .title("Javalin petstore")
                .description("Test API on petstore")
        )

    Javalin.create()
        .port(8080)
        .enableCorsForAllOrigins() // for editor.swagger.io
        .routes {
            post("pet", documented(
                route()
                    .description("Add a new pet to the store")
                    .request()
                        .description("Pet object that needs to be added to the store")
                        .content(
                            Content()
                                .entry(
                                    withMime("application/json")
                                        .schema(Pet::class.java)
                                        .example(Pet(0, Category(0, "string"), "doggie", listOf("string"), listOf(Tag(0, "string")), PetStatus.AVAILABLE))
                                )
                        )
                    .response()
                        .add(
                            withStatus(405)
                                .description("Invalid input")
                        )
                    .build(),
                {}
            ))
        }
        .serveSwagger(api)
        .start()
}

data class Pet(
    @Property
    val id: Long,
    @Property
    val category: Category,
    @Property(required = true)
    val name: String,
    @Property(required = true)
    val photoUrls: List<String>,
    @Property
    val tags: List<Tag>,
    @Property
    val status: PetStatus
)

data class Category(
    val id: Long,
    val name: String
)

data class Tag(
    val id: Long,
    val name: String
)

@Schema(description = "pet status in the store")
enum class PetStatus(private val value: String) {
    AVAILABLE("available"),
    PENDING("pending"),
    SOLD("sold");

    override fun toString() = value
}