package dev.extframework.mixin.api

public annotation class Opcode(
    val value: Int = -1,
    val vars : IntArray = [],
    val ldc : LDC = LDC("", true),

    val defaulted: Boolean = false,
)