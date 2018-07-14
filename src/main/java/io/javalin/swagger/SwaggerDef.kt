package io.javalin.swagger

import io.swagger.v3.oas.annotations.enums.ParameterIn

//#region Factory functions

fun route() = Route()

fun parameter(name: String, location: ParameterIn) = Parameter(name, location).also { ParameterBuilder.route?.add(it) }

fun content() = Content()

fun withMime(mimeType: String) = ContentEntry(mimeType)

fun withStatus(status: Int) = ResponseEntry(status.toString())

fun withStatus(status: String) = ResponseEntry(status)

//#endregion

//#region Route

class Route {
    private val response = Response(this)
    private val request = Request( this)
    private var description: String? = null
    private var summary: String? = null
    private var id: String? = null
    private var tag: String? = null
    private var deprecated: Boolean? = null
    private val parameters = mutableListOf<Parameter>()

    fun request() = request
    fun response() = response

    fun description(description: String) = this.apply { this.description = description }
    fun description() = description

    fun summary(summary: String) = this.apply { this.summary = summary }
    fun summary() = summary

    fun id(id: String) = this.apply { this.id = id }
    fun id() = id

    fun tag(tag: String) = this.apply { this.tag = tag }
    fun tag() = tag

    fun deprecated(deprecated: Boolean) = this.apply { this.deprecated = deprecated }
    fun deprecated() = deprecated

    fun params(closure: () -> Unit): Route {
        synchronized(ParameterBuilder::class) {
            ParameterBuilder.start(this)
            closure()
            ParameterBuilder.build()
        }
        return this
    }
    internal fun add(parameter: Parameter) = this.apply { this.parameters.add(parameter) }

    fun params(): List<Parameter> = parameters

    fun build() = this
}

private object ParameterBuilder {

    internal var route: Route? = null

    fun start(route: Route) {
        this.route = route
    }

    fun build() {
        this.route = null
    }
}

class Parameter(private val name: String, private val location: ParameterIn) {
    private var description: String? = null
    private var required: Boolean? = null
    private var schema: Class<*>? = null

    fun name() = name
    fun location() = location

    fun description(description: String) = this.apply { this.description = description }
    fun description() = description

    fun required(required: Boolean) = this.apply { this.required = required }
    fun required() = required

    fun schema(schema: Class<*>) = this.apply { this.schema = schema }
    fun schema() = schema
}

//#endregion

//#region Request

class Request(private val route: Route) {
    private var description: String? = null
    private var content: Content? = null

    fun response() = route.response()

    fun description(description: String) = this.apply { this.description = description }
    fun description() = description

    fun content(content: Content) = this.apply { this.content = content }
    fun content() = content

    fun build() = route
}

class Content {
    private val entries = mutableListOf<ContentEntry>()

    fun entry(entry: ContentEntry) = this.also { this.entries.add(entry) }
    fun entries(): List<ContentEntry> = entries
}

class ContentEntry(private val mimeType: String) {
    private var schema: Class<*>? = null
    private var example: Any? = null

    fun mime() = mimeType

    fun schema(schema: Class<*>) = this.also { this.schema = schema }
    fun schema() = schema as? Class<Any>

    fun example(example: Any) = this.also { this.example = example }
    fun example(): Any? = example
}

//#endregion

//#region Response

class Response(private val route: Route) {
    private val entries = mutableListOf<ResponseEntry>()

    fun add(entry: ResponseEntry) = this.also { entries.add(entry) }
    fun entries(): List<ResponseEntry> = entries

    fun build() = route

    fun request() = route.request()
}

class ResponseEntry(private val status: String) {
    private var description: String? = null
    private var content: Content? = null

    fun status() = status

    fun description(description: String) = this.apply { this.description = description }
    fun description() = description

    fun content(content: Content) = this.apply { this.content = content }
    fun content() = content
}

//#endregion