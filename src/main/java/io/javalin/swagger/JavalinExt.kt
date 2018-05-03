package io.javalin.swagger

import io.javalin.Javalin
import io.javalin.core.HandlerType
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths

@JvmOverloads
fun Javalin.serveSwagger(openAPI: OpenAPI, path: String = "swagger.yaml"): Javalin {
    val routes = this.routeOverviewEntries
        .filter { it.handler is DocumentedHandler }
        .map { it.path to (it.httpMethod to it.handler as DocumentedHandler) }
        .groupBy({ it.first }, { it.second })
        .entries
        .map {
            it.key.escapeParams() to it.value.associateBy({ it.first }, { it.second })
        }
        .fold(Paths(), { acc, (path, map) ->
            val pathItem = PathItem()
            map.entries.forEach {
                val op = when (it.key) {
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
                op(it.value.operation)
            }
            acc.addPathItem(path, pathItem)
        })

    openAPI.paths(routes)
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
