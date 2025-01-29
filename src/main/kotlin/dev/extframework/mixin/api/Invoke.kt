package dev.extframework.mixin.api

import kotlin.reflect.KClass

public annotation class Invoke(
    val value: KClass<*>,
    val method : String,
    val opcode: Int = -1,

    val defaulted: Boolean = false,
)
