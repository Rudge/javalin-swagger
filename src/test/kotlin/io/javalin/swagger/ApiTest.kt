package io.javalin.swagger

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.apibuilder.ApiBuilder.put
import io.javalin.swagger.annotations.Property
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag as SwaggerTag

fun main(args: Array<String>) {

    val api = OpenAPI()
            .info(Info()
                    .version("1.0.0")
                    .title("Swagger Petstore")
                    .description("Test API on petstore")
                    .license(License().name("MIT"))
            )
            .addServersItem(Server().url("http://localhost:8080"))
            .addServersItem(Server().url("http://petstore.swagger.io/v1"))
            .addTagsItem(SwaggerTag()
                    .description("Everything about your pets")
                    .name("pet")
            )

    val petController = PetController()
    Javalin.create()
            .port(8080)
            .enableCorsForAllOrigins() // for editor.swagger.io
            .enableCaseSensitiveUrls()
            .routes {
                path("pets") {
                    documented(get(petController::get)) {
                        route().summary("List all pets")
                                .id("listPets")
                                .tag("pets")
                                .add(Parameter("limit", ParameterIn.QUERY)
                                        .description("How many items to return at one time (max 100)")
                                        .schema(Int::class.java)
                                )
                                .response()
                                .add(withStatus(200)
                                        .description("A paged array of pets")
//                                        .headers()
                                        .content(Content().entry(withMimeJson().schema(Array<Pet>::class.java)))
                                )
                                .add(withStatus("default").description("unexpected error")
                                        .content(Content().entry(withMimeJson().schema(Error::class.java))))
                                .build()
                    }
                }
                path("pet") {
                    documented(post(petController::add)) {
                        route().summary("Add a new pet to the store")
                                .tag("pet")
                                .request().description("Pet object that needs to be added to the store")
                                .content(Content().entry(withMimeJson().schema(Pet::class.java)
                                        .example(Pet(0, Category(0, "string"), "doggie",
                                                listOf("string"), listOf(Tag(0, "string")), PetStatus.AVAILABLE))))

                                .response()
                                .add(withStatus(405).description("Invalid input"))
                                .build()
                    }
                    documented(put(petController::update)) {
                        route().summary("Update an existing pet")
                                .tag("pet")
                                .request()
                                .description("Pet object that needs to be added to the store")
                                .content(Content().entry(withMimeJson().schema(Pet::class.java)))
                                .response()
                                .add(withStatus(405).description("Validation exception"))
                                .add(withStatus(400).description("Invalid ID supplied"))
                                .add(withStatus(404).description("Pet not found"))
                                .build()
                    }

                    documented(get("findByStatus", petController::findByStatus)) {
                        route().summary("Finds Pets by status")
                                .description("Multiple status values can be provided with comma separated strings")
                                .tag("pet")
                                .add(Parameter("status", ParameterIn.QUERY)
                                        .description("Status values that need to be considered for filter")
                                        .schema(PetStatus::class.java)
                                        .required(true)
                                )
                                .response()
                                .add(withStatus(200)
                                        .description("Successful operation")
                                        .content(Content().entry(withMimeJson().schema(Pet::class.java)))
                                )
                                .add(withStatus(400).description("Invalid status value"))
                                .build()
                    }

                    documented(get("findByTag", petController::findByTag)) {
                        route().deprecated(true)
                                .summary("Finds Pets by tags")
                                .description("Multiple tags can be provided with comma separated strings. Use tag1, tag2, tag3 for testing.")
                                .tag("pet")
                                .params {
                                    parameter("tags", ParameterIn.QUERY)
                                            .required(true)
                                            .description("Tags to filter by")
                                            .schema(String::class.java)
                                }
                                .response()
                                .add(withStatus(200).description("Successful operation")
                                        .content(Content().entry(withMimeJson().schema(Pet::class.java)))
                                )
                                .add(withStatus(200).description("Invalid tag value"))
                                .build()
                    }

                }
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

data class Error(
        @Property
        val code: Int,
        @Property
        val message: String)