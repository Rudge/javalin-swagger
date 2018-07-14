package io.javalin.swagger

import io.javalin.Javalin
import io.javalin.core.HandlerType
import io.javalin.embeddedserver.Location
import io.javalin.swagger.annotations.Property
import io.javalin.swagger.annotations.Schema as SchemaAnn
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.*
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.media.Content as SwaggerContent
import io.swagger.v3.oas.models.parameters.Parameter as SwaggerParameter

private val schemas = mutableMapOf<String, Schema<*>>()
private val primitiveSchemaTypes = setOf("string", "number")

@JvmOverloads
fun Javalin.serveSwagger(openAPI: OpenAPI, path: String = "swagger/yaml"): Javalin {

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

    val paths = routes.fold(Paths()) { acc, (path, map) ->
        val pathItem = PathItem()
        val noop = { _: Operation -> }
        map.entries.forEach { (type, handler) ->
            val op = when (type) {
                HandlerType.GET -> pathItem::get
                HandlerType.POST -> pathItem::post
                HandlerType.PUT -> pathItem::put
                HandlerType.PATCH -> pathItem::patch
                HandlerType.DELETE -> pathItem::delete
                HandlerType.HEAD -> pathItem::head
                HandlerType.TRACE -> pathItem::trace
                HandlerType.CONNECT -> noop
                HandlerType.OPTIONS -> pathItem::options
                HandlerType.BEFORE -> noop
                HandlerType.AFTER -> noop
                HandlerType.INVALID -> noop
                HandlerType.WEBSOCKET -> noop
            }
            val operation = handler.route.asOperation()
            op(operation)
        }
        acc.addPathItem(path, pathItem)
    }

    val components = Components()
    schemas.forEach { key, schema ->
        components.addSchemas(key, schema)
    }
    openAPI.components(components)
    openAPI.paths(paths)
    val configString = Yaml.pretty(openAPI)

    get(path) { ctx ->
        ctx.result(configString)
            .status(200)
            .contentType("application/x-yaml")
    }

    enableStaticFiles("swagger/ui/", Location.CLASSPATH)

    return this
}

private fun String.escapeParams(): String {
    val params = split("/").filter { it.startsWith(":") }
    val replacements = params.map {
        "{" + it.replace(":", "") + "}"
    }
    return params.zip(replacements)
        .fold(this) { acc, (param, replacement) ->
            acc.replace(param, replacement)
        }
}

private fun Route.asOperation(): Operation {
    val route = this

    val operation =  Operation()
        .description(route.description())
        .operationId(route.id())

    operation.parameters(
        route.params()
            .map { it.asSwagger() }
    )

    val request = route.request()
    if (request.description() != null && request.content() != null) {
        operation.requestBody(
            RequestBody()
                .description(request.description())
                .content(request.content()?.asSwagger()))
    }

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
    parameter.schema(parameter.schema() ?: String::class.java)

    return SwaggerParameter()
        .name(parameter.name())
        .description(parameter.description())
        .`in`(parameter.location().toString())
        .required(parameter.required())
        .schema(parameter.schema()?.parseSchema(null))
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
        .schema(entry.schema()?.parseSchema(entry.example()))
}

private fun ResponseEntry.asSwagger(): ApiResponse {
    val entry = this
    return ApiResponse()
        .description(entry.description())
        .content(entry.content()?.asSwagger())
}

private fun <T> Class<T>.parseSchema(example: T?): Schema<T> {
    val cls = this
    // TODO: How should we parse the schema?
    val type = when {
        cls.isAssignableFrom(String::class.java) -> "string"
        cls.isPrimitive && cls in setOf(Long::class.java, Int::class.java) -> "number"
        cls.isEnum -> "string"
        else -> "object"
    }

    if (primitiveSchemaTypes.contains(type)) {
        return Schema<T>().also { schema ->
            schema.type = type
            schema.example = example

            if (cls.isEnum) {
                schema.description = cls.description()
                schema.enum = cls.enumConstants.toMutableList()
            }
        }
    } else {
        val key = cls.simpleName
        val schema = schemas.getOrPut(key) {
            Schema<T>().also {
                it.type = type
                val props = cls.parseProperties()
                it.properties = props.properties
                it.required = props.required
            }
        }
        if (example != null && schema.example == null) {
            schema.example = example
        }
        return Schema<T>().apply { `$ref` = "#/components/schemas/$key" }
    }
}

private fun <T> Class<T>.parseProperties(): Schema<*> {
    val cls = this
    val propertyCls = Property::class.java

    val properties = cls.declaredFields.filter { it.isAnnotationPresent(propertyCls) }
        .map { Triple(it.name, it.getAnnotation(propertyCls).required, it.type) }

    val methods = cls.methods.filter { it.isAnnotationPresent(propertyCls) }
        .map { Triple(it.name.clearMethodName(), it.getAnnotation(propertyCls).required, it.returnType) }

    val schema = Schema<T>()
    schema.description = cls.description()
    (properties + methods).forEach { (name, required, cls) ->
        val propSchema = cls.parseSchema(null)
        schema.addProperties(name, propSchema)
        if (required) {
            schema.addRequiredItem(name)
        }
    }
    return schema
}

private fun <T> Class<T>.description(): String? {
    val schemaCls = SchemaAnn::class.java

    return if (isAnnotationPresent(schemaCls)) {
        val schema = getAnnotation(schemaCls)
        if (schema.description.isEmpty()) null else schema.description
    } else {
        null
    }
}

private fun String.clearMethodName() = replace("(^get|^set)".toRegex(), "").decapitalize()
