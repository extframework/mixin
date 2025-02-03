package dev.extframework.mixin.api

import kotlin.reflect.KClass

public annotation class Invoke(
    val value: KClass<*> = Nothing::class,
    // Failsafe in case you cannot reference the actual type.
    val method : String,
    val opcode: Int = -1,
    val clsName : String = "",

    val defaulted: Boolean = false,
)
