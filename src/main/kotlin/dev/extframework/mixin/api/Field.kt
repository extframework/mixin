package dev.extframework.mixin.api

import kotlin.reflect.KClass

public annotation class Field(
    val value: KClass<*>,
    val name : String,
    val type : FieldAccessType = FieldAccessType.EITHER,

    val defaulted: Boolean = false,
)
