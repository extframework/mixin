package dev.extframework.mixin.api

import kotlin.reflect.KClass

public annotation class Custom(
    val value: KClass<out InstructionSelector>,
    val options: Array<String> = [],

    val defaulted: Boolean = false,
)
