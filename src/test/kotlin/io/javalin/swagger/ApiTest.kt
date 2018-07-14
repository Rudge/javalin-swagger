package io.javalin.swagger

import io.javalin.ApiBuilder.*
import io.javalin.Javalin
import io.javalin.swagger.DocumentedHandler.documented
import io.javalin.swagger.annotations.Property
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.enums.ParameterIn.*
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.tags.Tag as SwaggerTag

fun main(args: Array<String>) {

    val api = OpenAPI()
        .info(
            Info()
                .title("Javalin petstore")
                .description("Test API on petstore")
        )
        .addTagsItem(
            SwaggerTag()
                .description("Everything about your pets")
                .name("pet")
        )

    Javalin.create()
        .port(8080)
        .enableCorsForAllOrigins() // for editor.swagger.io
        .routes {
            post("pet", documented(
                route()
                    .summary("Add a new pet to the store")
                    .tag("pet")
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

            put("pet", documented(
                route()
                    .summary("Update an existing pet")
                    .tag("pet")
                    .request()
                    .description("Pet object that needs to be added to the store")
                    .content(
                        Content()
                            .entry(
                                withMime("application/json")
                                    .schema(Pet::class.java)
                            )
                    )
                    .response()
                    .add(
                        withStatus(405)
                            .description("Validation exception")
                    )
                    .add(
                        withStatus(400)
                            .description("Invalid ID supplied")
                    )
                    .add(
                        withStatus(404)
                            .description("Pet not found")
                    )
                    .build(),
                {}
            ))

            get("pet/findByStatus", documented(
                route()
                    .summary("Finds Pets by status")
                    .description("Multiple status values can be provided with comma separated strings")
                    .tag("pet")
                    .add(
                        Parameter("status", QUERY)
                            .description("Status values that need to be considered for filter")
                            .schema(PetStatus::class.java)
                            .required(true)
                    )
                    .response()
                        .add(
                            withStatus(200)
                                .description("Successful operation")
                                .content(
                                    Content().entry(
                                        withMime("application/json")
                                            .schema(Pet::class.java)
                                    )
                                )
                        )
                        .add(
                            withStatus(400)
                                .description("Invalid status value")
                        )
                    .build(),
                {}
            ))

            get("pet/findByTag", documented(
                route()
                    .deprecated(true)
                    .summary("Finds Pets by tags")
                    .description("Multiple tags can be provided with comma separated strings. Use tag1, tag2, tag3 for testing.")
                    .tag("pet")
                    .params {
                        parameter("tags", QUERY)
                            .required(true)
                            .description("Tags to filter by")
                            .schema(String::class.java)
                    }
                    .response()
                        .add(
                            withStatus(200)
                                .description("Successful operation")
                                .content(
                                    Content()
                                        .entry(
                                            withMime("application/json")
                                                .schema(Pet::class.java)
                                        )
                                )
                        )
                        .add(
                            withStatus(200)
                                .description("Invalid tag value")
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
    @Property
    val id: Long,
    @Property
    val name: String
)

data class Tag(
    @Property
    val id: Long,
    @Property
    val name: String
)

@Schema(description = "pet status in the store")
enum class PetStatus(private val value: String) {
    AVAILABLE("available"),
    PENDING("pending"),
    SOLD("sold");

    override fun toString() = value
}