package io.javalin.swagger

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.parameters.Parameter

fun route() = Route()

data class Route(var operation: Operation? = null, var parameters: MutableList<Parameter>? = null) {
    fun operation(operation: Operation): Route = this.also { this.operation = operation }
    fun param(param: Parameter): Route = this.also {
        if (parameters == null) {
            parameters = mutableListOf()
        }
        parameters?.add(param)
    }
}
