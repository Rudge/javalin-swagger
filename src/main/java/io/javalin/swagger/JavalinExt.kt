package io.javalin.swagger

import io.javalin.Javalin
import io.javalin.core.HandlerType
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.*
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.media.Content as SwaggerContent
import io.swagger.v3.oas.models.parameters.Parameter as SwaggerParameter

val schemas = mutableMapOf<Class<*>, String>()

@JvmOverloads
fun Javalin.serveSwagger(openAPI: OpenAPI, path: String = "swagger.yaml"): Javalin {

    schemas.clear()

    val routes = this.routeOverviewEntries
        .asSequence()
        .filter { it.handler is DocumentedHandler }
        .map { it.path to (it.httpMethod to it.handler as DocumentedHandler) }
        .groupBy({ it.first }, { it.second })
        .entries
        .map {
            it.key.escapeParams() to it.value.associateBy({ it.first }, { it.second })
        }

    val paths = routes.fold(Paths(), { acc, (path, map) ->
        val pathItem = PathItem()
        map.entries.forEach { (type, handler) ->
            val op = when (type) {
                HandlerType.GET -> pathItem::get
                HandlerType.POST -> pathItem::post
                HandlerType.PUT -> pathItem::put
                HandlerType.PATCH -> pathItem::patch
                HandlerType.DELETE -> pathItem::delete
                HandlerType.HEAD -> pathItem::head
                HandlerType.TRACE -> pathItem::trace
                HandlerType.CONNECT -> TODO()
                HandlerType.OPTIONS -> pathItem::options
                HandlerType.BEFORE -> TODO()
                HandlerType.AFTER -> TODO()
                HandlerType.INVALID -> TODO()
                HandlerType.WEBSOCKET -> TODO()
            }
            val operation = handler.route.asOperation()
            op(operation)
        }
        acc.addPathItem(path, pathItem)
    })

    val components = Components()
    schemas.forEach { cls, _ ->
        // TODO: How should we parse the schema?
        val type = when {
            cls.isAssignableFrom(String::class.java) -> "string"
            cls.isAssignableFrom(Number::class.java) -> "number"
            else -> "object"
        }
        components.addSchemas(cls.simpleName, Schema<Any>().also { it.type = type })
    }
    openAPI.components(components)
    openAPI.paths(paths)
    val configString = Yaml.pretty(openAPI)

    get(path, { ctx ->
        ctx.result(configString)
            .status(200)
            .contentType("application/x-yaml")
    })

    return this
}

private fun String.escapeParams(): String {
    val params = split("/").filter { it.startsWith(":") }
    val replacements = params.map {
        "{" + it.replace(":", "") + "}"
    }
    return params.zip(replacements)
        .fold(this, { acc, (param, replacement) ->
            acc.replace(param, replacement)
        })
}

private fun Route.asOperation(): Operation {
    val route = this

    val operation =  Operation()
        .description(route.description())
        .operationId(route.id())

    route.params()
        .forEach {
            operation.addParametersItem(it.asSwagger())
        }

    val request = route.request()
    operation.requestBody(
        RequestBody()
            .description(request.description())
            .content(content().asSwagger()))

    val response = route.response()
    val swaggerResponses = ApiResponses()
    response.entries().forEach {
        swaggerResponses.addApiResponse(it.status(), it.asSwagger())
    }
    operation.responses(swaggerResponses)

    return operation
}

private fun Parameter.asSwagger(): SwaggerParameter {
    val parameter = this
    return SwaggerParameter()
        .name(parameter.name())
        .description(parameter.description())
        .`in`(parameter.location().toString())
        .required(parameter.required())
        .schema(parameter.schema()?.asSwagger())
}

private fun <T> Class<T>.asSwagger(): Schema<T> {
    val cls = this
    if (!schemas.containsKey(cls)) {
        schemas[cls] = "#/components/schemas/" + cls.simpleName
    }
    val schema = Schema<T>()
    schema.`$ref` = schemas[cls]
    return schema
}

private fun Content.asSwagger(): SwaggerContent {
    val content = this
    val swaggerContent = SwaggerContent()

    content.entries().forEach {
        swaggerContent.addMediaType(it.mime(), it.asMediaType())
    }

    return swaggerContent
}

private fun ContentEntry.asMediaType(): MediaType {
    val entry = this
    return MediaType()
        .schema(entry.schema()?.asSwagger())
        .examples(entry.examples().entries.map {
            it.key to Example().value(it.value)
        }.toMap())
}

private fun ResponseEntry.asSwagger(): ApiResponse {
    val entry = this
    return ApiResponse()
        .description(entry.description())
        .content(entry.content()?.asSwagger())
}
