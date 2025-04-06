package dev.extframework.mixin.api

import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
public annotation class Mixin(
    val value: KClass<*> = Nothing::class,
    val targets: Array<KClass<*>> = [],
)
