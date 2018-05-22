package io.javalin.swagger.annotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Property(val required: Boolean = false)
