package dev.extframework.mixin.api

public data class UninitializedType(
    val type: ClassReference
) {
    override fun toString(): String {
        return "Uninitialized $type"
    }
}
