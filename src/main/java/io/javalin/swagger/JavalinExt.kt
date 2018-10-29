package io.javalin.swagger

import io.javalin.Javalin
import io.javalin.core.HandlerType
import io.javalin.swagger.annotations.Property
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import java.lang.reflect.ParameterizedType
import java.util.*
import io.javalin.swagger.annotations.Schema as SchemaAnn
import io.swagger.v3.oas.models.media.Content as SwaggerContent
import io.swagger.v3.oas.models.parameters.Parameter as SwaggerParameter

private val schemas = mutableMapOf<String, Schema<*>>()
private val documentedRoutes = ArrayDeque<Route>()

fun documented(app: Unit, route: () -> Route): Route {
    val route = route()
    documentedRoutes.add(route)
    return route
}

@JvmOverloads
fun Javalin.serveSwagger(openAPI: OpenAPI, path: String = "api-docs"): Javalin {

    schemas.clear()

    val routes = this.handlerMetaInfo
            .asSequence()
            .filter { it.path != "*" }
            .map { it.path to (it.httpMethod to it.handler) }
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
            val operation = asOperation(documentedRoutes.pop())
            if (handler.javaClass.isAnnotationPresent(Deprecated::class.java)) {
                operation.deprecated = true
            }
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

    enableWebJars()
//    get(path, SwaggerRenderer("spec.yaml"))
    get(path) { ctx ->
        ctx.result(configString)
                .status(200)
//                .contentType("application/x-yaml")
    }

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

private fun asOperation(route: Route): Operation {
    val operation = Operation()
            .description(route.description())
            .summary(route.summary())
            .operationId(route.id())
            .addTagsItem(route.tag())
            .deprecated(route.deprecated())

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

private fun <T> Class<T>.parseSchema(example: T?, genericType: Class<*>? = null): Schema<*> {
    val cls = this
    val formatType = FormatType.getByClass(cls)

    when {
        cls.isPrimitive -> {
            return Schema<T>().type(formatType?.type).format(formatType?.format)
        }
        cls.isArray -> {
            val nameRef = "${cls.componentType.simpleName}s"
            val keyRef = "#/components/schemas/$nameRef"
            val schemaArrayRef = Schema<T>().also { schema ->
                schema.`$ref` = keyRef
                schema.example = example
            }
            val schemaObjectRef = Schema<T>().also { schema ->
                schema.`$ref` = "#/components/schemas/${cls.componentType.simpleName}"
            }
            schemas[nameRef] = ArraySchema().items(schemaObjectRef)
            return schemaArrayRef
        }
        cls.isEnum -> {
            return Schema<T>().type("string")
                    .example(example)
                    .description(cls.description())
                    .also { it.enum = cls.enumConstants.toList() }
        }
        cls.isAssignableFrom(List::class.java) -> {
            return ArraySchema().also {
                it.items = genericType?.parseSchema(null, null)
            }.also { schema ->
                schema.type = "array"
                schema.example = example
            }
        }
        else -> {
            val key = cls.simpleName
            val schema = schemas.getOrPut(key) {
                Schema<T>().also {
                    it.type = "object"
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
}

private fun <T> Class<T>.parseProperties(): Schema<*> {
    val cls = this
    val propertyCls = Property::class.java

    val properties = cls.declaredFields.filter { it.isAnnotationPresent(propertyCls) }
            .map { Triple(Pair(it.type, it.genericType as? ParameterizedType), it.name, it.getAnnotation(propertyCls).required) }

    val methods = cls.methods.filter { it.isAnnotationPresent(propertyCls) }
            .map { Triple(Pair(it.returnType, it.genericReturnType as? ParameterizedType), it.name.clearMethodName(), it.getAnnotation(propertyCls).required) }

    val schema = Schema<T>()
    schema.description = cls.description()
    (properties + methods).forEach { (clsType, name, required) ->
        val (cls, type) = clsType
        val propSchema = cls.parseSchema(null, type?.actualTypeArguments?.firstOrNull() as? Class<*>)
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
